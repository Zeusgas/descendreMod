package fr.descendre.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.descendre.client.DescendreClientCubeCache;
import fr.descendre.client.render.backend.DescendreRenderPass;
import fr.descendre.world.cube.CubePos;
import fr.descendre.world.cube.DescendreCube;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import fr.descendre.client.render.backend.DescendreRenderBackend;

/**
 * Itère sur tous les cubes du cache client et délègue le rendu à DescendreCubeRenderer.
 *
 * Appelé une fois par frame depuis DescendreClientEvents.
 */
public final class DescendreRenderDispatcher {

    private DescendreRenderDispatcher() {}

    public static void renderAll(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            Vec3 cameraPos,
            DescendreRenderPass pass
    ) {
        DescendreClientCubeCache cache = DescendreClientCubeCache.get();
        if (cache.size() == 0) return;

        DescendreMeshCache.get().beginFrame(4);

        final int renderRadiusCubes = 12;
        final double maxDistSq = (renderRadiusCubes * 16.0) * (renderRadiusCubes * 16.0);

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

            if (dx * dx + dy * dy + dz * dz > maxDistSq) {
                continue;
            }

            DescendreCubeMesh mesh = DescendreCubeMesh.build(cube, DescendreClientCubeCache.getInstance());

            if (mesh == null || mesh.isEmpty()) {
                continue;
            }

            DescendreRenderBackend.renderer().render(
                    mesh,
                    poseStack,
                    bufferSource,
                    cameraPos,
                    pass
            );
        }
    }
}