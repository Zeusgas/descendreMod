package fr.descendre.mixin.collision;

import fr.descendre.collision.DescendreCollisionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hook Level.getBlockState pour que les blocs cubic soient "vus" par tout
 * le système vanilla (raycast, clic-droit, clic-gauche, particules, etc.).
 *
 * Stratégie : si vanilla retourne AIR à une position donnée, on regarde si
 * un bloc cubic existe à cette position. Si oui, on le retourne à la place.
 * Si non, on laisse l'air vanilla.
 *
 * Conséquence : aucune perf perdue dans les zones où il y a déjà des blocs vanilla.
 */
@Mixin(Level.class)
public abstract class LevelGetBlockStateMixin {

    @Inject(
            method = "getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("RETURN"),
            cancellable = true
    )
    private void descendre$overrideAirWithCubic(
            BlockPos pos,
            CallbackInfoReturnable<BlockState> cir
    ) {
        // D'abord vérifie si on est dans un contexte cubic ThreadLocal
        fr.descendre.world.DescendreCubeLevel.Context ctx = fr.descendre.world.DescendreCubeLevel.current();
        if (ctx != null) {
            net.minecraft.world.level.block.state.BlockState cubic = ctx.map.getBlock(pos);
            if (cubic != null && !cubic.isAir()) {
                cir.setReturnValue(cubic);
                return;
            }
        }

        // Sinon, le code normal
        BlockState vanilla = cir.getReturnValue();
        if (vanilla == null || !vanilla.isAir()) return;

        Level level = (Level) (Object) this;
        BlockState cubic = DescendreCollisionProvider.lookupBlock(level, pos);
        if (cubic != null && !cubic.isAir()) {
            cir.setReturnValue(cubic);
        }
    }
}