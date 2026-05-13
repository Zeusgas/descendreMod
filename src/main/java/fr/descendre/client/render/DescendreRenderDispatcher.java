package fr.descendre.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.descendre.client.DescendreClientCubeCache;
import fr.descendre.client.render.backend.DescendreRenderPass;
import fr.descendre.client.render.backend.DescendreRenderBackend;
import fr.descendre.world.cube.CubePos;
import fr.descendre.world.cube.DescendreCube;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

public final class DescendreRenderDispatcher {

    public static final int RENDER_RADIUS_CUBES = 8;
    public static final int MAX_BUILDS_PER_FRAME = 4;

    /** Flush le buffer tous les N cubes pour éviter l'overflow (limite ~16M vertices). */
    private static final int FLUSH_EVERY = 8;

    private static final double NEAR_BUBBLE_DIST_SQ = 24.0 * 24.0;

    /** Si on est haut dans la couronne (Y > 2500), rayon réduit pour éviter d'over-render. */
    private static final double LEAF_ZONE_Y = 2500.0;
    private static final double LEAF_ZONE_RADIUS_SQ = 64.0 * 64.0;

    private DescendreRenderDispatcher() {}

    public static void renderAll(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            Vec3 cameraPos,
            DescendreRenderPass pass
    ) {
        DescendreClientCubeCache cache = DescendreClientCubeCache.get();
        if (cache.size() == 0) return;

        DescendreMeshCache meshCache = DescendreMeshCache.get();
        if (pass == DescendreRenderPass.OPAQUE) {
            meshCache.beginFrame(MAX_BUILDS_PER_FRAME);
        }

        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        org.joml.Vector3fc forwards = camera.forwardVector();
        double lookX = forwards.x();
        double lookY = forwards.y();
        double lookZ = forwards.z();

        final double maxDistSq = (RENDER_RADIUS_CUBES * 16.0) * (RENDER_RADIUS_CUBES * 16.0);

        int cubesRenderedThisFrame = 0;

        for (DescendreCube cube : cache.allCubes()) {
            if (cube.isEmpty()) continue;

            CubePos pos = cube.pos();

            // Optim : caméra en surface → pas de souterrain profond (Y < -32)
            if (cameraPos.y > -5 && pos.y() < -2) continue;

            // Optim inverse : caméra profonde → pas de surface lointaine
            if (cameraPos.y < -100 && Math.abs(pos.y() - (int)(cameraPos.y / 16)) > 5) continue;

            double worldOriginX = pos.x() << 4;
            double worldOriginY = pos.y() << 4;
            double worldOriginZ = pos.z() << 4;

            double centerX = worldOriginX + 8.0;
            double centerY = worldOriginY + 8.0;
            double centerZ = worldOriginZ + 8.0;

            double dx = centerX - cameraPos.x;
            double dy = centerY - cameraPos.y;
            double dz = centerZ - cameraPos.z;

            double distSq = dx * dx + dy * dy + dz * dz;

            // Optim "dans la couronne" : si on est très haut, on réduit le rayon de rendu
            // (les feuilles forment un volume dense et opaque, on ne voit pas loin)
            if (cameraPos.y > LEAF_ZONE_Y) {
                if (distSq > LEAF_ZONE_RADIUS_SQ) continue;
            } else {
                if (distSq > maxDistSq) continue;
            }

            // Direction culling : skip si cube derrière la caméra (sauf bulle proche)
            if (distSq > NEAR_BUBBLE_DIST_SQ) {
                double dot = dx * lookX + dy * lookY + dz * lookZ;
                if (dot < 0) continue;
            }

            DescendreCubeMesh mesh = meshCache.getOrBuild(pos, DescendreClientCubeCache.getInstance());
            if (mesh == null || mesh.isEmpty()) continue;

            DescendreRenderBackend.renderer().render(mesh, poseStack, bufferSource, cameraPos, pass);

            cubesRenderedThisFrame++;
            // Flush périodique pour éviter le buffer overflow
            if (cubesRenderedThisFrame % FLUSH_EVERY == 0
                    && bufferSource instanceof MultiBufferSource.BufferSource bs) {
                bs.endBatch();
            }
        }
    }
}