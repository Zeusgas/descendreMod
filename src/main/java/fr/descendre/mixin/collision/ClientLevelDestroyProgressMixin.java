package fr.descendre.mixin.collision;

import fr.descendre.client.DescendreClientCubeCache;
import fr.descendre.client.render.DescendreBreakProgress;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hook ClientLevel.destroyBlockProgress pour rediriger les positions cubic vers
 * notre cache de fissures (DescendreBreakProgress) au lieu du LevelRenderer vanilla
 * qui tronque Y via BlockPos.asLong().
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelDestroyProgressMixin {

    @Inject(
            method = "destroyBlockProgress",
            at = @At("HEAD"),
            cancellable = true
    )
    private void descendre$redirectCubicBreakProgress(
            int playerId,
            BlockPos pos,
            int progress,
            CallbackInfo ci
    ) {
        ClientLevel self = (ClientLevel) (Object) this;

        // Si position dans la range vanilla : on laisse vanilla faire
        if (pos.getY() >= self.getMinY() && pos.getY() <= self.getMaxY()) {
            BlockState cubic = DescendreClientCubeCache.get().getBlock(pos);
            // S'il y a un bloc cubic à cette position (rare dans la range vanilla, mais possible),
            // on redirige aussi chez nous pour être cohérent
            if (cubic == null || cubic.isAir()) return;
        }

        // Position cubic : redirige vers notre cache
        DescendreBreakProgress.get().set(playerId, pos, progress);

        if (pos.getY() < -1000 && progress >= 0 && progress < 10) {
            System.out.println("[BREAK-MIXIN] redirect playerId=" + playerId + " pos=" + pos + " progress=" + progress);
        }
        DescendreBreakProgress.get().set(playerId, pos, progress);
        ci.cancel();

    }
}