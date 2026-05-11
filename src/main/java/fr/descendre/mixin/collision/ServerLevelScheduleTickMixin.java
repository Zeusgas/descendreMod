package fr.descendre.mixin.collision;

import fr.descendre.core.DescendreHeight;
import fr.descendre.server.DescendreScheduledTicks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.ticks.TickPriority;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * Intercepte les scheduled ticks de blocs.
 *
 * En 1.21.11, scheduleTick(BlockPos, Block, int) est dans ScheduledTickAccess,
 * pas directement dans ServerLevel ni LevelAccessor.
 */
@Mixin(ScheduledTickAccess.class)
public interface ServerLevelScheduleTickMixin {

    /**
     * @author Descendre
     * @reason Rediriger les scheduled ticks cubic hors hauteur vanilla.
     */
    @Overwrite
    default void scheduleTick(BlockPos pos, Block block, int delay) {
        this.scheduleTick(pos, block, delay, TickPriority.NORMAL);
    }

    /**
     * @author Descendre
     * @reason Rediriger les scheduled ticks cubic hors hauteur vanilla.
     */
    @Overwrite
    default void scheduleTick(BlockPos pos, Block block, int delay, TickPriority priority) {
        ScheduledTickAccess self = (ScheduledTickAccess) (Object) this;

        if ((Object) this instanceof ServerLevel level) {
            boolean outsideVanilla =
                    pos.getY() < level.getMinY() || pos.getY() >= level.getMaxY();

            if (outsideVanilla) {
                if (DescendreHeight.isInsideInternalRange(pos.getY())) {
                    DescendreScheduledTicks.get(level)
                            .schedule(pos, block, delay, level.getGameTime());
                }
                return;
            }
        }

        self.getBlockTicks().schedule(
                self.createTick(pos, block, delay, priority)
        );
    }
}