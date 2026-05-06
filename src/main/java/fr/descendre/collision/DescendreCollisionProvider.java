package fr.descendre.collision;

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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DescendreCollisionProvider {

    private DescendreCollisionProvider() {}

    /**
     * Retourne les collisions Descendre qui croisent la boîte demandée.
     *
     * Version J7b :
     * - serveur uniquement
     * - tout bloc non-air = cube plein 1x1x1
     */
    public static List<VoxelShape> getCollisionShapes(Level level, Entity entity, AABB collisionBox) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return Collections.emptyList();
        }

        CubeMap map = DescendreCubeManager.get(serverLevel);

        AABB box = collisionBox.inflate(1.0E-7D);

        int minX = Mth.floor(box.minX);
        int maxX = Mth.floor(box.maxX);
        int minY = Mth.floor(box.minY);
        int maxY = Mth.floor(box.maxY);
        int minZ = Mth.floor(box.minZ);
        int maxZ = Mth.floor(box.maxZ);

        List<VoxelShape> shapes = new ArrayList<>();
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

                    VoxelShape shape = Shapes.create(
                            new AABB(
                                    x,
                                    y,
                                    z,
                                    x + 1.0D,
                                    y + 1.0D,
                                    z + 1.0D
                            )
                    );

                    shapes.add(shape);
                }
            }
        }

        return shapes;
    }
}