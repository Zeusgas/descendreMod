package fr.descendre.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.descendre.client.DescendreClientCubeCache;
import fr.descendre.world.cube.CubePos;
import fr.descendre.world.cube.DescendreCube;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

/**
 * Itère sur tous les cubes du cache client et délègue le rendu à DescendreCubeRenderer.
 *
 * Appelé une fois par frame depuis DescendreClientEvents.
 */
public final class DescendreRenderDispatcher {

    private DescendreRenderDispatcher() {}

    public static void renderAll(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cameraPos) {
        DescendreClientCubeCache cache = DescendreClientCubeCache.get();
        if (cache.size() == 0) return;

        DescendreMeshCache.get().beginFrame(4);

        // Distance de rendu temporaire côté client.
        // 12 cubes = 192 blocs autour de la caméra.
        // Plus tard on mettra ça dans la config.
        final int renderRadiusCubes = 12;
        final double maxDistSq = (renderRadiusCubes * 16.0) * (renderRadiusCubes * 16.0);

        for (DescendreCube cube : cache.allCubes()) {
            if (cube.isEmpty()) continue;

            CubePos pos = cube.pos();

            double worldOriginX = pos.x() << 4;
            double worldOriginY = pos.y() << 4;
            double worldOriginZ = pos.z() << 4;

            // Centre du cube pour le test de distance
            double centerX = worldOriginX + 8.0;
            double centerY = worldOriginY + 8.0;
            double centerZ = worldOriginZ + 8.0;

            double dx = centerX - cameraPos.x;
            double dy = centerY - cameraPos.y;
            double dz = centerZ - cameraPos.z;

            if (dx * dx + dy * dy + dz * dz > maxDistSq) {
                continue;
            }

            poseStack.pushPose();
            poseStack.translate(
                    worldOriginX - cameraPos.x,
                    worldOriginY - cameraPos.y,
                    worldOriginZ - cameraPos.z
            );

            DescendreCubeRenderer.renderCube(poseStack, bufferSource, cube);

            poseStack.popPose();
        }
    }
}