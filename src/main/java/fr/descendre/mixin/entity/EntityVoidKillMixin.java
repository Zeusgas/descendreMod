package fr.descendre.mixin.entity;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityVoidKillMixin {

    /*
     * Minecraft tue normalement les entités quand elles sont sous la build height réelle.
     * Pour Descendre, on autorise la zone virtuelle jusqu'à environ -15000.
     *
     * Si l'entité tombe beaucoup plus bas que notre zone, on laisse Minecraft la tuer.
     */
    private static final double DESCENDRE_VOID_KILL_Y = -15128.0D;

    @Inject(
            method = "checkBelowWorld",
            at = @At("HEAD"),
            cancellable = true
    )
    private void descendre$disableVanillaVoidKill(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;

        if (self.getY() > DESCENDRE_VOID_KILL_Y) {
            ci.cancel();
        }
    }
}