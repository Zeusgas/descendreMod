package fr.descendre.world.collision;

import fr.descendre.server.DescendreCubeManager;
import fr.descendre.world.cube.CubeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

public final class DescendreCollision {
    private static final double EPSILON = 1.0E-7D;

    private DescendreCollision() {}

    public static List<VoxelShape> collect(ServerLevel level, Entity entity, AABB box) {
        CubeMap map = DescendreCubeManager.get(level);
        List<VoxelShape> shapes = new ArrayList<>();

        CollisionContext context = entity == null
                ? CollisionContext.empty()
                : CollisionContext.of(entity);

        AABB searchBox = box.inflate(EPSILON);

        map.forEachNonAirBlock(searchBox, (pos, state) -> {
            VoxelShape shape = state.getCollisionShape(level, pos, context);

            if (!shape.isEmpty()) {
                shapes.add(shape.move(pos.getX(), pos.getY(), pos.getZ()));
            }
        });

        return shapes;
    }
}