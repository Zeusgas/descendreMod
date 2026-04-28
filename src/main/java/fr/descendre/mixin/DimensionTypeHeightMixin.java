package fr.descendre.mixin;

import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DimensionType.class)
public abstract class DimensionTypeHeightMixin {

    @Inject(method = "minY", at = @At("RETURN"), cancellable = true)
    private void descendre$minY(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(-15008);
    }

    @Inject(method = "height", at = @At("RETURN"), cancellable = true)
    private void descendre$height(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(20016);
    }

    @Inject(method = "logicalHeight", at = @At("RETURN"), cancellable = true)
    private void descendre$logicalHeight(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(20016);
    }
}