package fr.descendre.mixin;

import com.google.common.collect.Iterables;
import fr.descendre.world.collision.DescendreCollision;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Mixin(Entity.class)
public abstract class EntityCollisionMixin {

    @Redirect(
            method = "collideBoundingBox(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Lnet/minecraft/world/level/Level;Ljava/util/List;)Lnet/minecraft/world/phys/Vec3;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getBlockCollisions(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/lang/Iterable;"
            ),
            require = 0
    )
    private static Iterable<VoxelShape> descendre$addCubicCollisionLevel(Level level, Entity entity, AABB box) {
        Iterable<VoxelShape> vanilla = level.getBlockCollisions(entity, box);

        if (!(level instanceof ServerLevel serverLevel)) {
            return vanilla;
        }

        List<VoxelShape> cubic = DescendreCollision.collect(serverLevel, entity, box);

        if (cubic.isEmpty()) {
            return vanilla;
        }

        return Iterables.concat(vanilla, cubic);
    }

    @Redirect(
            method = "collideBoundingBox(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Lnet/minecraft/world/level/Level;Ljava/util/List;)Lnet/minecraft/world/phys/Vec3;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/CollisionGetter;getBlockCollisions(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/lang/Iterable;"
            ),
            require = 0
    )
    private static Iterable<VoxelShape> descendre$addCubicCollisionGetter(CollisionGetter getter, Entity entity, AABB box) {
        Iterable<VoxelShape> vanilla = getter.getBlockCollisions(entity, box);

        if (!(getter instanceof ServerLevel serverLevel)) {
            return vanilla;
        }

        List<VoxelShape> cubic = DescendreCollision.collect(serverLevel, entity, box);

        if (cubic.isEmpty()) {
            return vanilla;
        }

        return Iterables.concat(vanilla, cubic);
    }
}