package fr.descendre.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.descendre.client.DescendreClientCubeCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache et rendu des animations de cassage progressif (fissure noire) pour les blocs cubic.
 *
 * Vanilla LevelRenderer utilise BlockPos.asLong() comme clé interne, ce qui tronque Y à 12 bits.
 * Donc pour les positions Y hors de [-2032, 2031], la fissure ne s'affiche pas avec le système
 * vanilla. On maintient notre propre map et on dessine nous-mêmes via RenderLevelStageEvent.
 *
 * Côté monojoueur :
 *   - MultiPlayerGameMode.continueDestroyBlock appelle level.destroyBlockProgress(playerId, pos, progress)
 *   - Notre mixin redirige vers DescendreBreakProgress.set(playerId, pos, progress)
 *   - À chaque frame, on rend toutes les fissures cubic
 *
 * En multi : il faudra synchroniser via packets (TODO).
 */
public final class DescendreBreakProgress {

    private static long lastLogNs = 0;

    private static final DescendreBreakProgress INSTANCE = new DescendreBreakProgress();

    public static DescendreBreakProgress get() {
        return INSTANCE;
    }

    /** Pour chaque playerId qui casse un bloc cubic : la position et la progression. */
    private final Map<Integer, ProgressInfo> active = new ConcurrentHashMap<>();

    private DescendreBreakProgress() {}

    public record ProgressInfo(BlockPos pos, int progress) {}

    /** Set ou update la progression. progress 0-9 = en cours, < 0 ou >= 10 = supprime. */
    public void set(int playerId, BlockPos pos, int progress) {
        if (progress >= 0 && progress < 10) {
            active.put(playerId, new ProgressInfo(pos.immutable(), progress));
        } else {
            active.remove(playerId);
        }
    }

    public void remove(int playerId) {
        active.remove(playerId);
    }

    public void clear() {
        active.clear();
    }

    /** Rend toutes les fissures actives. Appelé depuis RenderLevelStageEvent. */
    public void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, double cameraX, double cameraY, double cameraZ) {
        if (active.isEmpty()) return;

        if (active.size() > 0) {
            // Spam protection : log seulement 1 frame sur 20
            if (System.nanoTime() / 50_000_000L != lastLogNs) {
                lastLogNs = System.nanoTime() / 50_000_000L;
                System.out.println("[BREAK-RENDER] active=" + active.size());
            }
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        DescendreClientCubeCache cache = DescendreClientCubeCache.get();

        // Regroupe par position pour ne dessiner qu'une fois par bloc, avec la plus haute progression
        Map<BlockPos, Integer> highest = new HashMap<>();
        for (ProgressInfo info : active.values()) {
            highest.merge(info.pos(), info.progress(), Math::max);
        }

        for (Map.Entry<BlockPos, Integer> entry : highest.entrySet()) {
            BlockPos pos = entry.getKey();
            int progress = entry.getValue();

            BlockState state = cache.getBlock(pos);
            if (state == null || state.isAir()) continue;

            double dx = pos.getX() + 0.5 - cameraX;
            double dy = pos.getY() + 0.5 - cameraY;
            double dz = pos.getZ() + 0.5 - cameraZ;
            if (dx * dx + dy * dy + dz * dz > 1024.0) continue;

            // === LOG ===
            if (System.nanoTime() / 100_000_000L != lastLogNs) {
                lastLogNs = System.nanoTime() / 100_000_000L;
                System.out.println("[BREAK-RENDER-INNER] pos=" + pos + " progress=" + progress
                        + " state=" + state + " renderShape=" + state.getRenderShape()
                        + " distSqr=" + (dx*dx+dy*dy+dz*dz));
            }

            poseStack.pushPose();
            poseStack.translate(pos.getX() - cameraX, pos.getY() - cameraY, pos.getZ() - cameraZ);
            PoseStack.Pose pose = poseStack.last();

            VertexConsumer vc = new SheetedDecalTextureGenerator(
                    bufferSource.getBuffer(ModelBakery.DESTROY_TYPES.get(progress)),
                    pose, 1.0F
            );

            mc.getBlockRenderer().renderBreakingTexture(state, pos, mc.level, poseStack, vc);
            poseStack.popPose();
        }
    }
}