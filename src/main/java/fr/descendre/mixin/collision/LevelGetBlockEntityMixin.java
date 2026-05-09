package fr.descendre.mixin.collision;

import fr.descendre.server.DescendreCubeManager;
import fr.descendre.world.cube.CubeMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hook Level.getBlockEntity pour que vanilla "voie" les BlockEntity cubic.
 * Crucial pour que les coffres, fours, panneaux puissent être ouverts/utilisés via vanilla.
 *
 * Côté serveur : lit dans CubeMap.getBlockEntity(pos)
 * Côté client : lit dans DescendreClientCubeCache.getBlockEntity(pos)
 */
@Mixin(Level.class)
public abstract class LevelGetBlockEntityMixin {

    @Inject(
            method = "getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;",
            at = @At("RETURN"),
            cancellable = true
    )
    private void descendre$getBlockEntityCubic(BlockPos pos, CallbackInfoReturnable<BlockEntity> cir) {
        BlockEntity vanilla = cir.getReturnValue();
        if (vanilla != null) return; // vanilla a déjà trouvé, on ne touche pas

        Level level = (Level) (Object) this;
        if (level instanceof ServerLevel serverLevel) {
            CubeMap map = DescendreCubeManager.get(serverLevel);
            BlockEntity cubic = map.getBlockEntity(pos);
            if (cubic != null) {
                cir.setReturnValue(cubic);
            }
        } else if (level instanceof ClientLevel) {
            BlockEntity cubic = fr.descendre.client.DescendreClientCubeCache.get().getBlockEntity(pos);
            if (cubic != null) {
                cir.setReturnValue(cubic);
            }
        }
    }
}