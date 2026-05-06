package fr.descendre.cubic;

import fr.descendre.core.DescendreHeight;
import fr.descendre.server.DescendreCubeManager;
import fr.descendre.world.cube.CubeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

public final class DescendreCubicCollision {

    private static final int MAX_SCANNED_BLOCKS = 8192;

    private DescendreCubicCollision() {
    }

    public static void appendCollisions(
            Entity entity,
            Level level,
            AABB searchBox,
            List<VoxelShape> shapes
    ) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        CubeMap map = DescendreCubeManager.get(serverLevel);

        int minX = Mth.floor(searchBox.minX) - 1;
        int maxX = Mth.floor(searchBox.maxX) + 1;

        int minY = Mth.floor(searchBox.minY) - 1;
        int maxY = Mth.floor(searchBox.maxY) + 1;

        int minZ = Mth.floor(searchBox.minZ) - 1;
        int maxZ = Mth.floor(searchBox.maxZ) + 1;

        int scanX = maxX - minX + 1;
        int scanY = maxY - minY + 1;
        int scanZ = maxZ - minZ + 1;

        if (scanX <= 0 || scanY <= 0 || scanZ <= 0) {
            return;
        }

        if (scanX * scanY * scanZ > MAX_SCANNED_BLOCKS) {
            return;
        }

        CollisionContext context = entity == null
                ? CollisionContext.empty()
                : CollisionContext.of(entity);

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int y = minY; y <= maxY; y++) {
            if (!DescendreHeight.isInsideInternalRange(y)) {
                continue;
            }

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    pos.set(x, y, z);

                    BlockState state = map.getBlock(pos);

                    if (state == null || state.isAir()) {
                        continue;
                    }

                    VoxelShape shape = state.getCollisionShape(level, pos, context);

                    if (!shape.isEmpty()) {
                        shapes.add(shape.move(x, y, z));
                    }
                }
            }
        }
    }
}