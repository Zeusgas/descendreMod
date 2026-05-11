package fr.descendre.client;

import fr.descendre.client.render.DescendreRenderDispatcher;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import fr.descendre.client.render.DescendreBreakProgress;
import fr.descendre.client.DescendreClientCubeCache;

/**
 * Hook côté client : appelé à chaque frame après le rendu des blocs translucides.
 */
public final class DescendreClientEvents {

    private DescendreClientEvents() {}

    public static void register(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.AfterOpaqueBlocks.class,
                DescendreClientEvents::onRenderOpaque);

        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.AfterTranslucentBlocks.class,
                DescendreClientEvents::onRenderTranslucent);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.ClientTickEvent.Post.class,
                DescendreClientEvents::onClientTick);
    }

    private static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        DescendreClientCubeCache.get().tickBlockEntities();
    }
    private static void onRenderOpaque(RenderLevelStageEvent.AfterOpaqueBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.position();

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        DescendreRenderDispatcher.renderAll(
                event.getPoseStack(),
                bufferSource,
                cameraPos,
                fr.descendre.client.render.backend.DescendreRenderPass.OPAQUE
        );

        bufferSource.endBatch();
    }

    private static void onRenderTranslucent(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.position();

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        DescendreRenderDispatcher.renderAll(
                event.getPoseStack(),
                bufferSource,
                cameraPos,
                fr.descendre.client.render.backend.DescendreRenderPass.TRANSLUCENT
        );

        bufferSource.endBatch();

        MultiBufferSource.BufferSource crumblingBuffer = mc.renderBuffers().crumblingBufferSource();
        fr.descendre.client.render.DescendreBreakProgress.get().render(
                event.getPoseStack(),
                crumblingBuffer,
                cameraPos.x,
                cameraPos.y,
                cameraPos.z
        );
        crumblingBuffer.endBatch();
    }
}