package fr.descendre.mixin.collision;

import fr.descendre.client.DescendreClientCubeCache;
import fr.descendre.network.ServerboundCubicBlockActionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hook côté client pour synchroniser les actions sur les blocs cubic avec le serveur,
 * tout en laissant vanilla gérer l'inventaire, l'animation, les drops, les sons, etc.
 *
 * Stratégie :
 *  - On NE bloque PAS le pipeline vanilla (pas de cir.setReturnValue).
 *  - On ENVOIE un packet supplémentaire au serveur avec la position en int32.
 *  - Le packet vanilla part aussi (avec position tronquée Y) mais échouera côté serveur
 *    silencieusement car la position tronquée ne correspond à aucun bloc.
 *  - Notre packet, lui, fait le vrai changement côté serveur.
 *
 * Conséquence : vanilla fait toute sa logique normale (drops, inventaire, animation),
 * et notre serveur fait le vrai changement de bloc cubic.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {

    @Inject(
            method = "destroyBlock(Lnet/minecraft/core/BlockPos;)Z",
            at = @At("HEAD")
    )
    private void descendre$onDestroyBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!isCubicBlock(pos)) return;
        if (!canPlayerInteract()) return;
        // Envoie le packet custom : le serveur changera le CubeMap sur la VRAIE position
        ClientPacketDistributor.sendToServer(ServerboundCubicBlockActionPacket.breakAt(pos));
    }

    @Inject(
            method = "startDestroyBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z",
            at = @At("HEAD")
    )
    private void descendre$onStartDestroyBlock(BlockPos pos, Direction dir, CallbackInfoReturnable<Boolean> cir) {
        // Note : startDestroyBlock est appelé au début de l'animation (survie). En créatif,
        // c'est destroyBlock qui est appelé directement (insta-break). On ne fait rien ici
        // pour ne pas envoyer le packet trop tôt — on attend stopDestroyBlock (TODO).
        // Pour l'instant, c'est destroyBlock qui couvre 99% des cas (créatif).
    }

    @Inject(
            method = "useItemOn(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",
            at = @At("HEAD")
    )
    private void descendre$onUseItemOn(
            LocalPlayer player,
            InteractionHand hand,
            BlockHitResult hit,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        BlockPos targetPos = hit.getBlockPos();
        if (!isCubicBlock(targetPos)) return;

        BlockPos placePos = targetPos.relative(hit.getDirection());
        BlockState toPlace = player.getItemInHand(hand).getItem() instanceof net.minecraft.world.item.BlockItem bi
                ? bi.getBlock().defaultBlockState()
                : null;
        if (toPlace == null) return;

        // Vérifie que la position est libre côté cubic
        BlockState existing = DescendreClientCubeCache.get().getBlock(placePos);
        if (existing != null && !existing.isAir()) return;

        if (!canPlayerInteract()) return;

        // Aventure : vérifie que l'item peut être placé sur le bloc visé (CanPlaceOn)
        if (Minecraft.getInstance().player.gameMode() == net.minecraft.world.level.GameType.ADVENTURE) {
            net.minecraft.world.item.ItemStack stack = player.getItemInHand(hand);
            net.minecraft.world.item.AdventureModePredicate canPlace = stack.get(net.minecraft.core.component.DataComponents.CAN_PLACE_ON);
            BlockState targetState = DescendreClientCubeCache.get().getBlock(targetPos);
            if (canPlace == null || targetState == null || !canPlace.test(new net.minecraft.world.level.block.state.pattern.BlockInWorld(player.level(), targetPos, false))) {
                return;
            }
        }

        // Envoie le packet custom : le serveur posera dans le CubeMap sur la VRAIE position
        ClientPacketDistributor.sendToServer(ServerboundCubicBlockActionPacket.placeAt(placePos, toPlace));
    }

    /** True si la position pointe vers un bloc cubic (Y hors range OU bloc cubic présent). */
    private static boolean isCubicBlock(BlockPos pos) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return false;

        if (pos.getY() >= level.getMinY() && pos.getY() <= level.getMaxY()) {
            BlockState cubic = DescendreClientCubeCache.get().getBlock(pos);
            return cubic != null && !cubic.isAir();
        }
        return true;
    }



    /**
     * True si le joueur peut interagir avec les blocs (créatif, survie ou aventure avec permission).
     * False en spectateur.
     */
    private static boolean canPlayerInteract() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        net.minecraft.world.level.GameType mode = mc.player.gameMode();
        return mode != net.minecraft.world.level.GameType.SPECTATOR;
    }

}