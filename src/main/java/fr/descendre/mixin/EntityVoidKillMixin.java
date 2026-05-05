package fr.descendre.mixin;

import fr.descendre.core.DescendreHeight;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityVoidKillMixin {

    /*
     * Vanilla tue les entités très basses sous le monde réel Minecraft.
     *
     * Pour Descendre, on veut autoriser le joueur à exister entre :
     * -15000 et +5000.
     *
     * Donc si l'entité est encore au-dessus de INTERNAL_MIN_Y - 1024,
     * on annule le check vanilla "fell out of the world".
     */
    @Inject(method = "checkBelowWorld", at = @At("HEAD"), cancellable = true)
    private void descendre$preventVoidKillInsideDescendreRange(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;

        double killLimit = DescendreHeight.INTERNAL_MIN_Y - 1024.0;

        if (self.getY() >= killLimit) {
            ci.cancel();
        }
    }
}