package fr.descendre.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.descendre.client.DescendreClientCubeCache;
import fr.descendre.world.cube.DescendreCube;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;

import java.util.List;
import java.util.Map;

/**
 * Émet les quads d'un cube à partir de son mesh pré-calculé.
 *
 * Plus aucun cull, plus aucun model lookup, plus aucune allocation par frame.
 * Tout le travail a déjà été fait par DescendreCubeMesh.build().
 */
public final class DescendreCubeRenderer {

    private static final int FULL_LIGHT = 0x00F000F0; // sky 15, block 15

    private DescendreCubeRenderer() {}

    public static void renderCube(PoseStack poseStack, MultiBufferSource bufferSource, DescendreCube cube) {
        if (cube.isEmpty()) return;

        DescendreClientCubeCache cubeCache = DescendreClientCubeCache.get();
        DescendreCubeMesh mesh = DescendreMeshCache.get().getOrBuild(cube.pos(), cubeCache);
        if (mesh == null || mesh.isEmpty()) return;

        PoseStack.Pose pose = poseStack.last();

        for (Map.Entry<RenderType, List<DescendreCubeMesh.QuadEntry>> entry : mesh.quadsByType().entrySet()) {
            RenderType renderType = entry.getKey();
            List<DescendreCubeMesh.QuadEntry> quads = entry.getValue();
            if (quads.isEmpty()) continue;

            VertexConsumer consumer = bufferSource.getBuffer(renderType);

            for (DescendreCubeMesh.QuadEntry q : quads) {
                poseStack.pushPose();
                poseStack.translate(q.lx(), q.ly(), q.lz());
                emitQuad(consumer, poseStack.last(), q.quad());
                poseStack.popPose();
            }
        }
    }

    private static void emitQuad(VertexConsumer consumer, PoseStack.Pose pose, BakedQuad quad) {
        consumer.putBulkData(pose, quad, 1.0F, 1.0F, 1.0F, 1.0F, FULL_LIGHT, OverlayTexture.NO_OVERLAY);
    }
}