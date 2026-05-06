package fr.descendre.mixin.collision;

import fr.descendre.cubic.DescendreCubicCollision;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;

@Mixin(Entity.class)
public abstract class EntityCollisionMixin {

    @ModifyVariable(
            method = "collideBoundingBox",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private static List<VoxelShape> descendre$addCubicCollisionShapes(
            List<VoxelShape> originalShapes,
            Entity entity,
            Vec3 movement,
            AABB box,
            Level level
    ) {
        ArrayList<VoxelShape> mergedShapes = new ArrayList<>(originalShapes);

        AABB searchBox = box
                .expandTowards(movement)
                .inflate(0.000001D);

        DescendreCubicCollision.appendCollisions(
                entity,
                level,
                searchBox,
                mergedShapes
        );

        return mergedShapes;
    }
}