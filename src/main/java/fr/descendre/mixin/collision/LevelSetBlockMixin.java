package fr.descendre.mixin.collision;

import fr.descendre.collision.DescendreCollisionProvider;
import fr.descendre.core.DescendreHeight;
import fr.descendre.server.DescendreCubeManager;
import fr.descendre.world.cube.CubeMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hook Level.setBlock pour rediriger les écritures vers CubeMap quand :
 *  - Y est hors range vanilla (donc géré par cubic)
 *  - OU il y a déjà un bloc cubic à cette position (qu'on remplace ou qu'on casse)
 *
 * Stratégie : on annule complètement l'écriture vanilla et on redirige vers
 * notre système cubic. Cubic broadcast déjà aux clients (via le change listener).
 */
@Mixin(Level.class)
public abstract class LevelSetBlockMixin {

    @Inject(
            method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void descendre$redirectToCubic(
            BlockPos pos,
            BlockState newState,
            int flags,
            CallbackInfoReturnable<Boolean> cir
    ) {
        Level level = (Level) (Object) this;

        // Log TOUS les setBlock pour Y < -1000 (sans filtre)
        if (pos.getY() < -1000) {
            System.out.println("[SETBLOCK-RAW] side=" + (level.isClientSide() ? "CLIENT" : "SERVER")
                    + " pos=" + pos + " state=" + newState
                    + " thread=" + Thread.currentThread().getName()
                    + " stackTop=" + new Throwable().getStackTrace()[1]);
        }

        boolean outOfVanillaRange = pos.getY() < level.getMinY() || pos.getY() > level.getMaxY();
        boolean cubicBlockExists = !outOfVanillaRange
                && DescendreCollisionProvider.lookupBlock(level, pos) != null;

        if (!outOfVanillaRange && !cubicBlockExists) return; // laisse vanilla faire son boulot

        if (!DescendreHeight.isInsideInternalRange(pos.getY())) {
            cir.setReturnValue(false);
            return;
        }

        if (level instanceof ServerLevel serverLevel) {
            CubeMap map = DescendreCubeManager.get(serverLevel);
            map.setBlock(pos, newState);
            cir.setReturnValue(true);
            return;
        }

        if (level instanceof ClientLevel) {
            // Côté client : applique localement dans le cache cubic.
            // Permet à vanilla (BlockItem.useOn) de "voir" le bloc juste posé pour décrémenter
            // la stack, jouer le son, etc. Le serveur enverra ensuite la version officielle.
            fr.descendre.client.DescendreClientCubeCache.get().updateBlock(pos.immutable(), newState);
            cir.setReturnValue(true);
            return;
        }
    }
}