package fr.descendre.client.render.backend;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.descendre.client.render.DescendreCubeMesh;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;

import fr.descendre.client.DescendreClientCubeCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.client.renderer.rendertype.RenderTypes;


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

        renderFluids(mesh, bufferSource, cameraPos);

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

    private static void renderFluids(
            DescendreCubeMesh mesh,
            MultiBufferSource bufferSource,
            Vec3 cameraPos
    ) {
        if (mesh.fluids().isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        BlockRenderDispatcher dispatcher = minecraft.getBlockRenderer();
        BlockAndTintGetter tintGetter = minecraft.level;

        float offsetX = (float) (mesh.originX() - cameraPos.x);
        float offsetY = (float) (mesh.originY() - cameraPos.y);
        float offsetZ = (float) (mesh.originZ() - cameraPos.z);

        for (DescendreCubeMesh.FluidEntry fluidEntry : mesh.fluids()) {
            RenderType renderType = fluidEntry.fluidState().is(FluidTags.WATER)
                    ? RenderTypes.translucentMovingBlock()
                    : RenderTypes.solidMovingBlock();

            VertexConsumer rawConsumer = bufferSource.getBuffer(renderType);
            VertexConsumer shiftedConsumer = new OffsetVertexConsumer(rawConsumer, offsetX, offsetY, offsetZ);

            dispatcher.renderLiquid(
                    fluidEntry.worldPos(),
                    tintGetter,
                    shiftedConsumer,
                    fluidEntry.blockState(),
                    fluidEntry.fluidState()
            );
        }
    }

    private record OffsetVertexConsumer(
            VertexConsumer delegate,
            float offsetX,
            float offsetY,
            float offsetZ
    ) implements VertexConsumer {

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x + offsetX, y + offsetY, z + offsetZ);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            delegate.setColor(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer setColor(int argb) {
            delegate.setColor(argb);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            delegate.setLineWidth(width);
            return this;
        }
    }

}