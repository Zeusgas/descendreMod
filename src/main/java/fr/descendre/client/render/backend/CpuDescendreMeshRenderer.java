package fr.descendre.client.render.backend;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.descendre.client.render.DescendreCubeMesh;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

public final class CpuDescendreMeshRenderer implements DescendreMeshRenderer {

    public static final CpuDescendreMeshRenderer INSTANCE = new CpuDescendreMeshRenderer();

    private static final int FULL_BRIGHT = 0x00F000F0;

    private CpuDescendreMeshRenderer() {}

    @Override
    public void render(
            DescendreCubeMesh mesh,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            Vec3 cameraPos
    ) {
        if (mesh == null || mesh.isEmpty()) {
            return;
        }

        for (Map.Entry<RenderType, List<DescendreCubeMesh.QuadEntry>> entry : mesh.quadsByType().entrySet()) {
            RenderType renderType = entry.getKey();
            List<DescendreCubeMesh.QuadEntry> quads = entry.getValue();

            if (quads == null || quads.isEmpty()) {
                continue;
            }

            VertexConsumer consumer = bufferSource.getBuffer(renderType);

            for (DescendreCubeMesh.QuadEntry quadEntry : quads) {
                renderQuad(mesh, quadEntry, poseStack, consumer, cameraPos);
            }
        }
    }

    private static void renderQuad(
            DescendreCubeMesh mesh,
            DescendreCubeMesh.QuadEntry quadEntry,
            PoseStack poseStack,
            VertexConsumer consumer,
            Vec3 cameraPos
    ) {
        double renderX = mesh.originX() + quadEntry.lx() - cameraPos.x;
        double renderY = mesh.originY() + quadEntry.ly() - cameraPos.y;
        double renderZ = mesh.originZ() + quadEntry.lz() - cameraPos.z;

        poseStack.pushPose();
        poseStack.translate(renderX, renderY, renderZ);

        int tintColor = quadEntry.tintColor();

        float red = ((tintColor >> 16) & 255) / 255.0F;
        float green = ((tintColor >> 8) & 255) / 255.0F;
        float blue = (tintColor & 255) / 255.0F;

        consumer.putBulkData(
                poseStack.last(),
                quadEntry.quad(),
                red,
                green,
                blue,
                1.0F,
                FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY
        );

        poseStack.popPose();
    }
}