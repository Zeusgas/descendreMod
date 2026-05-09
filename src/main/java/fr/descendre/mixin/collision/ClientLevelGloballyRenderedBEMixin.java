package fr.descendre.mixin.collision;

import fr.descendre.client.DescendreClientCubeCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.Set;

/**
 * Hook ClientLevel.getGloballyRenderedBlockEntities pour injecter les BlockEntity cubic.
 *
 * Vanilla appelle cette méthode dans LevelRenderer.extractVisibleBlockEntities pour rendre
 * tous les BE qui ne sont pas dans des chunks (ex: bookshelves, pistons, etc). On profite
 * de ce hook pour faire rendre TOUS nos BE cubic, sans toucher au pipeline GPU lui-même.
 *
 * Conséquence : vanilla appelle automatiquement le bon BlockEntityRenderer pour chaque BE
 * de notre cache cubic, à sa vraie position monde.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelGloballyRenderedBEMixin {

    private static int DEBUG_COUNTER = 0;


    @Inject(
            method = "getGloballyRenderedBlockEntities",
            at = @At("RETURN"),
            cancellable = true
    )
    private void descendre$includeCubicBlockEntities(CallbackInfoReturnable<Set<BlockEntity>> cir) {
        Set<BlockEntity> vanilla = cir.getReturnValue();
        Set<BlockEntity> cubic = DescendreClientCubeCache.get().getAllBlockEntities();
        // Log avec un compteur pour ne pas spammer (1 sur 100 frames)
        if (DEBUG_COUNTER++ % 100 == 0 && !cubic.isEmpty()) {
            System.out.println("[BE-RENDER] getGloballyRenderedBE: vanilla=" + vanilla.size() + " cubic=" + cubic.size());
        }
        if (cubic.isEmpty()) return;

        // On combine les deux sets sans modifier l'original
        Set<BlockEntity> combined = new HashSet<>(vanilla);
        combined.addAll(cubic);
        cir.setReturnValue(combined);
    }
}