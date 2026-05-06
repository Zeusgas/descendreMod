package fr.descendre.mixin;

import fr.descendre.collision.DescendreCollisionProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.List;

@Mixin(Entity.class)
public abstract class EntityCollisionMixin {

    /**
     * Minecraft demande les collisions vanilla ici.
     * On ajoute les collisions Descendre dans la même liste.
     */
    @Redirect(
            method = "collideBoundingBox",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getBlockCollisions(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/lang/Iterable;"
            )
    )
    private static Iterable<VoxelShape> descendre$addCubicBlockCollisions(
            Level level,
            Entity entity,
            AABB collisionBox
    ) {
        Iterable<VoxelShape> vanillaCollisions = level.getBlockCollisions(entity, collisionBox);

        List<VoxelShape> merged = new ArrayList<>();

        for (VoxelShape shape : vanillaCollisions) {
            merged.add(shape);
        }

        merged.addAll(DescendreCollisionProvider.getCollisionShapes(level, entity, collisionBox));

        return merged;
    }
}