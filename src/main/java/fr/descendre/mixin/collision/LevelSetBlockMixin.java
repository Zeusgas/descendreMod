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
        // CONTEXT CUBIC : tout setBlock pendant useOn écrit dans CubeMap
        fr.descendre.world.DescendreCubeLevel.Context ctx = fr.descendre.world.DescendreCubeLevel.current();
        if (ctx != null) {
            if (!fr.descendre.core.DescendreHeight.isInsideInternalRange(pos.getY())) {
                cir.setReturnValue(false);
                return;
            }
            fr.descendre.world.DescendreCubeLevel.setBlock(pos, newState);
            cir.setReturnValue(true);
            return;
        }

        // Hors contexte : code normal
        Level level = (Level) (Object) this;
        boolean outOfVanillaRange = pos.getY() < level.getMinY() || pos.getY() > level.getMaxY();
        boolean cubicBlockExists = !outOfVanillaRange
                && DescendreCollisionProvider.lookupBlock(level, pos) != null;
        if (!outOfVanillaRange && !cubicBlockExists) return;
        if (!DescendreHeight.isInsideInternalRange(pos.getY())) {
            cir.setReturnValue(false);
            return;
        }
        if (level instanceof ServerLevel serverLevel) {
            CubeMap map = DescendreCubeManager.get(serverLevel);
            map.setBlockServer(pos, newState, serverLevel);
            cir.setReturnValue(true);
            return;
        }

        if (level instanceof ClientLevel) {
            fr.descendre.client.DescendreClientCubeCache.get().updateBlock(pos.immutable(), newState);
            cir.setReturnValue(true);
            return;
        }
    }
}