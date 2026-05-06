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

        for (DescendreCube cube : cache.allCubes()) {
            if (cube.isEmpty()) continue;

            CubePos pos = cube.pos();
            // Coin (0,0,0) du cube en monde
            double worldOriginX = pos.x() << 4;
            double worldOriginY = pos.y() << 4;
            double worldOriginZ = pos.z() << 4;

            // Translation camera-relative : on dessine relatif à la caméra
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