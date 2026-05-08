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
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepte les actions sur les blocs cubic côté client pour envoyer un packet custom
 * qui contient les vraies coordonnées int32 (Y non tronqué) et le contexte complet
 * du placement (face, hit location, main) pour que le serveur reconstitue l'action vanilla.
 *
 * Le pipeline vanilla continue normalement côté client (animations, sons, GUI, inventaire).
 * On envoie juste un packet supplémentaire pour que le serveur applique le bon changement.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {

    @Inject(
            method = "destroyBlock(Lnet/minecraft/core/BlockPos;)Z",
            at = @At("HEAD")
    )
    private void descendre$onDestroyBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!isCubicBlock(pos)) return;
        if (!canBreak()) return;

        // Récupère le hit result courant pour avoir la direction de la face
        Minecraft mc = Minecraft.getInstance();
        BlockHitResult hit = null;
        if (mc.hitResult instanceof BlockHitResult bhr && bhr.getBlockPos().equals(pos)) {
            hit = bhr;
        }
        if (hit == null) {
            // Fallback : pas de hit result disponible, on crée un hit result minimal
            hit = new BlockHitResult(pos.getCenter(), Direction.UP, pos, false);
        }

        ClientPacketDistributor.sendToServer(
                ServerboundCubicBlockActionPacket.breakFrom(hit, InteractionHand.MAIN_HAND)
        );
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
        if (!canPlace(player)) return;

        // Vérifie si le bloc visé est interactif (porte, coffre, levier, bouton, etc.)
        // Si oui, on ne pose RIEN, on laisse le clic être traité comme une interaction.
        // On envoie toujours le packet pour que le serveur décide entre interaction et placement
        BlockState placementState = (player.getItemInHand(hand).getItem() instanceof net.minecraft.world.item.BlockItem bi)
                ? bi.getBlock().defaultBlockState()
                : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        ClientPacketDistributor.sendToServer(
                ServerboundCubicBlockActionPacket.placeFrom(hit, hand, placementState)
        );

        // Sneak + bloc en main = placement forcé même sur bloc interactif
        // (vanilla bypass aussi l'interaction si sneaking, sauf cas spéciaux)
        // → si on est ici, on fait un placement normal

        // On vérifie qu'on a un item en main (sinon c'est une interaction avec le bloc, pas un placement)
        net.minecraft.world.item.ItemStack stack = player.getItemInHand(hand);

        // On envoie le packet avec toutes les infos de contexte (face, hit location, main)
        // Le serveur s'en servira pour reconstruire le UseOnContext et appeler BlockItem.useOn()

        // Note : on n'envoie que le defaultBlockState ici. Le serveur recalculera le bon state
        // via BlockItem.useOn() qui tient compte de la face, direction joueur, etc.
    }

    /** True si le joueur peut casser (tout sauf spectateur). */
    private static boolean canBreak() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        return mc.player.gameMode() != GameType.SPECTATOR;
    }

    /** True si le joueur peut placer (survie/créatif, pas spectateur ni aventure sans permission). */
    private static boolean canPlace(LocalPlayer player) {
        GameType mode = player.gameMode();
        if (mode == GameType.SPECTATOR) return false;
        if (mode == GameType.ADVENTURE) return false; // géré en survie/créatif seulement pour l'instant
        return true;
    }

    /** True si la position pointe vers un bloc cubic. */
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
     * Détecte les blocs interactifs : ceux qui réagissent au clic-droit (porte, coffre,
     * four, bouton, levier, trappe, etc.). On laisse vanilla gérer l'interaction au
     * lieu de poser un bloc.
     */
    private static boolean isInteractive(BlockState state) {
        net.minecraft.world.level.block.Block block = state.getBlock();
        // Détecte les principales catégories interactives
        if (block instanceof net.minecraft.world.level.block.DoorBlock) return true;
        if (block instanceof net.minecraft.world.level.block.TrapDoorBlock) return true;
        if (block instanceof net.minecraft.world.level.block.FenceGateBlock) return true;
        if (block instanceof net.minecraft.world.level.block.ButtonBlock) return true;
        if (block instanceof net.minecraft.world.level.block.LeverBlock) return true;
        if (block instanceof net.minecraft.world.level.block.ChestBlock) return true;
        if (block instanceof net.minecraft.world.level.block.BarrelBlock) return true;
        if (block instanceof net.minecraft.world.level.block.AbstractFurnaceBlock) return true;
        if (block instanceof net.minecraft.world.level.block.CraftingTableBlock) return true;
        if (block instanceof net.minecraft.world.level.block.EnchantingTableBlock) return true;
        if (block instanceof net.minecraft.world.level.block.AnvilBlock) return true;
        if (block instanceof net.minecraft.world.level.block.SmokerBlock) return true;
        if (block instanceof net.minecraft.world.level.block.BlastFurnaceBlock) return true;
        if (block instanceof net.minecraft.world.level.block.BedBlock) return true;
        if (block instanceof net.minecraft.world.level.block.NoteBlock) return true;
        if (block instanceof net.minecraft.world.level.block.JukeboxBlock) return true;
        // Conteneurs en général (entité de bloc avec inventaire)
        if (state.hasBlockEntity()) return true;

        return false;
    }


}
