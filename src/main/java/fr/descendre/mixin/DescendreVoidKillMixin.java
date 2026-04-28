package fr.descendre.mixin;

import fr.descendre.cubic.DescendreCubicConfig;
import fr.descendre.cubic.DescendreCubicMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class DescendreVoidKillMixin {

    @Inject(
            method = "checkBelowWorld",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void descendre$cancelBelowWorldCheck(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;

        if (!DescendreCubicMode.isEnabled()) {
            return;
        }

        double y = entity.getY();

        if (y >= DescendreCubicConfig.TARGET_MIN_Y - 512
                && y <= DescendreCubicConfig.TARGET_MAX_Y + 512) {
            ci.cancel();
        }
    }

    @Inject(
            method = "outOfWorld",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void descendre$cancelOutOfWorld(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;

        if (!DescendreCubicMode.isEnabled()) {
            return;
        }

        double y = entity.getY();

        if (y >= DescendreCubicConfig.TARGET_MIN_Y - 512
                && y <= DescendreCubicConfig.TARGET_MAX_Y + 512) {
            ci.cancel();
        }
    }
}