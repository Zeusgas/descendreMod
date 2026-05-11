package fr.descendre.mixin.collision;

import fr.descendre.collision.DescendreCollisionProvider;
import fr.descendre.world.DescendreCubeLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class LevelGetFluidStateMixin {

    @Inject(
            method = "getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;",
            at = @At("RETURN"),
            cancellable = true
    )
    private void descendre$getCubicFluidState(
            BlockPos pos,
            CallbackInfoReturnable<FluidState> cir
    ) {
        DescendreCubeLevel.Context ctx = DescendreCubeLevel.current();

        if (ctx != null) {
            BlockState dirty = ctx.dirtyOverride.get(pos.immutable());
            if (dirty != null) {
                cir.setReturnValue(dirty.getFluidState());
                return;
            }

            BlockState cubic = ctx.map.getBlock(pos);
            if (cubic != null) {
                FluidState fluid = cubic.getFluidState();
                if (fluid != null && !fluid.isEmpty()) {
                    cir.setReturnValue(fluid);
                    return;
                }
            }
        }

        Level level = (Level) (Object) this;
        BlockState cubic = DescendreCollisionProvider.lookupBlock(level, pos);

        if (cubic == null) {
            return;
        }

        FluidState fluid = cubic.getFluidState();
        if (fluid != null && !fluid.isEmpty()) {
            cir.setReturnValue(fluid);
        }
    }
}