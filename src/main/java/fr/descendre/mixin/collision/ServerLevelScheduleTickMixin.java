package fr.descendre.mixin.collision;

import fr.descendre.core.DescendreHeight;
import fr.descendre.server.DescendreScheduledTicks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.ticks.TickPriority;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(ScheduledTickAccess.class)
public interface ServerLevelScheduleTickMixin {

    /**
     * @author Descendre
     * @reason Rediriger les block ticks cubic.
     */
    @Overwrite
    default void scheduleTick(BlockPos pos, Block block, int delay) {
        this.scheduleTick(pos, block, delay, TickPriority.NORMAL);
    }

    /**
     * @author Descendre
     * @reason Rediriger les block ticks cubic.
     */
    @Overwrite
    default void scheduleTick(BlockPos pos, Block block, int delay, TickPriority priority) {
        ScheduledTickAccess self = (ScheduledTickAccess) (Object) this;

        if ((Object) this instanceof ServerLevel level && descendre$isCubic(level, pos)) {
            DescendreScheduledTicks.get(level).schedule(pos, block, delay, level.getGameTime());
            return;
        }

        self.getBlockTicks().schedule(self.createTick(pos, block, delay, priority));
    }

    /**
     * @author Descendre
     * @reason Rediriger les fluid ticks cubic.
     */
    @Overwrite
    default void scheduleTick(BlockPos pos, Fluid fluid, int delay) {
        this.scheduleTick(pos, fluid, delay, TickPriority.NORMAL);
    }

    /**
     * @author Descendre
     * @reason Rediriger les fluid ticks cubic.
     */
    @Overwrite
    default void scheduleTick(BlockPos pos, Fluid fluid, int delay, TickPriority priority) {
        ScheduledTickAccess self = (ScheduledTickAccess) (Object) this;

        if ((Object) this instanceof ServerLevel level && descendre$isCubic(level, pos)) {
            DescendreScheduledTicks.get(level).schedule(pos, fluid, delay, level.getGameTime());
            return;
        }

        self.getFluidTicks().schedule(self.createTick(pos, fluid, delay, priority));
    }

    private static boolean descendre$isCubic(ServerLevel level, BlockPos pos) {
        boolean outsideVanilla =
                pos.getY() < level.getMinY()
                        || pos.getY() >= level.getMaxY();

        return outsideVanilla && DescendreHeight.isInsideInternalRange(pos.getY());
    }
}