package fr.descendre.mixin.collision;

import fr.descendre.core.DescendreHeight;
import fr.descendre.server.DescendreCubeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FallingBlock.class)
public abstract class FallingBlockCubicTickMixin {

    @Inject(
            method = "tick(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void descendre$cubicFallingTick(
            BlockState state,
            ServerLevel level,
            BlockPos pos,
            RandomSource random,
            CallbackInfo ci
    ) {
        boolean cubicPos =
                DescendreHeight.isInsideInternalRange(pos.getY())
                        && (pos.getY() < level.getMinY() || pos.getY() >= level.getMaxY());

        if (!cubicPos) {
            return;
        }

        // On empêche le tick vanilla, car il est lié aux limites de hauteur vanilla.
        ci.cancel();

        BlockPos belowPos = pos.below();
        BlockState belowState = DescendreCubeManager.get(level).getBlock(belowPos);

        boolean canFall =
                belowState == null
                        || belowState.isAir()
                        || belowState.getCollisionShape(level, belowPos).isEmpty();

        if (!canFall) {
            return;
        }

        FallingBlockEntity.fall(level, pos, state);
    }
}