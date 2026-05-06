package fr.descendre.mixin.collision;

import fr.descendre.collision.DescendreCollisionProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.level.CollisionGetter;

import java.util.ArrayList;
import java.util.List;

@Mixin(CollisionGetter.class)
public interface LevelBlockCollisionMixin {

    @Inject(
            method = "getBlockCollisions(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/lang/Iterable;",
            at = @At("RETURN"),
            cancellable = true
    )
    default void descendre$addCubicBlockCollisions(
            Entity entity,
            AABB collisionBox,
            CallbackInfoReturnable<Iterable<VoxelShape>> cir
    ) {
        if (!(this instanceof Level level)) return;

        List<VoxelShape> merged = new ArrayList<>();
        Iterable<VoxelShape> vanilla = cir.getReturnValue();
        if (vanilla != null) {
            for (VoxelShape shape : vanilla) {
                merged.add(shape);
            }
        }
        merged.addAll(DescendreCollisionProvider.getCollisionShapes(level, entity, collisionBox));
        cir.setReturnValue(merged);
    }
}