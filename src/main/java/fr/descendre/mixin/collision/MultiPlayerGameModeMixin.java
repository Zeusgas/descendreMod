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
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hook côté client pour rediriger les actions sur les blocs cubic vers notre packet
 * Descendre, qui utilise des coordonnées int32 (alors que le packet vanilla tronque Y à 12 bits).
 *
 * Cas couverts :
 *  - destroyBlock : cassage instantané (créatif)
 *  - startDestroyBlock : début de cassage (survie - traité comme insta pour l'instant)
 *  - useItemOn : placement de bloc avec un BlockItem en main
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {

    @Inject(
            method = "destroyBlock(Lnet/minecraft/core/BlockPos;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void descendre$interceptDestroyBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!isCubicBlock(pos)) return;
        ClientPacketDistributor.sendToServer(ServerboundCubicBlockActionPacket.breakAt(pos));
        cir.setReturnValue(true);
    }

    @Inject(
            method = "startDestroyBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void descendre$interceptStartDestroyBlock(BlockPos pos, Direction dir, CallbackInfoReturnable<Boolean> cir) {
        if (!isCubicBlock(pos)) return;
        ClientPacketDistributor.sendToServer(ServerboundCubicBlockActionPacket.breakAt(pos));
        cir.setReturnValue(true);
    }

    @Inject(
            method = "useItemOn(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void descendre$interceptUseItemOn(
            LocalPlayer player,
            InteractionHand hand,
            BlockHitResult hit,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        BlockPos targetPos = hit.getBlockPos();
        if (!isCubicBlock(targetPos)) return;

        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            // Le joueur n'a pas un bloc en main : on ne fait rien (interaction impossible)
            cir.setReturnValue(InteractionResult.PASS);
            return;
        }

        // Position où poser le bloc = la face cliquée (offset depuis la position visée)
        BlockPos placePos = targetPos.relative(hit.getDirection());

        // Vérifie que la position de placement n'a pas déjà un bloc
        BlockState existing = DescendreClientCubeCache.get().getBlock(placePos);
        if (existing != null && !existing.isAir()) {
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }

        BlockState toPlace = blockItem.getBlock().defaultBlockState();
        ClientPacketDistributor.sendToServer(ServerboundCubicBlockActionPacket.placeAt(placePos, toPlace));

        // Consomme le clic droit pour que vanilla ne fasse pas son placement (qui irait à la mauvaise Y)
        cir.setReturnValue(InteractionResult.SUCCESS);
    }

    /** Renvoie true si la position pointe vers un bloc cubic (Y hors range OU bloc cubic présent). */
    private static boolean isCubicBlock(BlockPos pos) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return false;

        if (pos.getY() >= level.getMinY() && pos.getY() <= level.getMaxY()) {
            BlockState cubic = DescendreClientCubeCache.get().getBlock(pos);
            return cubic != null && !cubic.isAir();
        }
        return true;
    }
}