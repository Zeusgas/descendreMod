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

    /**
     * Demi-angle de vue effectif. ~80° par défaut = cos(40°) ≈ 0.77.
     * On garde une marge : on garde tout ce qui est entre 90° et 100° de l'axe caméra
     * (cos < 0) plus une bulle proche pour éviter le pop-out des cubes adjacents.
     */
    private static final double NEAR_BUBBLE_DIST_SQ = 24.0 * 24.0;

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

        // Vecteur de vue caméra (forward) pour le test "devant ou derrière"
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        org.joml.Vector3fc forwards = camera.forwardVector();
        double lookX = forwards.x();
        double lookY = forwards.y();
        double lookZ = forwards.z();

        final double maxDistSq = (RENDER_RADIUS_CUBES * 16.0) * (RENDER_RADIUS_CUBES * 16.0);

        for (DescendreCube cube : cache.allCubes()) {
            if (cube.isEmpty()) continue;

            CubePos pos = cube.pos();

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
            if (distSq > maxDistSq) continue;

            // Direction culling : skip si cube derrière la caméra
            // sauf s'il est très proche (bulle pour éviter pop-out)
            if (distSq > NEAR_BUBBLE_DIST_SQ) {
                double dot = dx * lookX + dy * lookY + dz * lookZ;
                if (dot < 0) continue;
            }

            DescendreCubeMesh mesh = meshCache.getOrBuild(pos, DescendreClientCubeCache.getInstance());
            if (mesh == null || mesh.isEmpty()) continue;

            DescendreRenderBackend.renderer().render(mesh, poseStack, bufferSource, cameraPos, pass);
        }
    }
}