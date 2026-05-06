package fr.descendre.client;

import fr.descendre.client.render.DescendreRenderDispatcher;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Hook côté client : appelé à chaque frame après le rendu des blocs translucides.
 */
public final class DescendreClientEvents {

    private DescendreClientEvents() {}

    public static void register(IEventBus modBus) {
        // RenderLevelStageEvent passe par le bus global NeoForge (côté game), pas le mod bus.
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.AfterTranslucentBlocks.class,
                DescendreClientEvents::onRender);
    }

    private static void onRender(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.position();

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        DescendreRenderDispatcher.renderAll(event.getPoseStack(), bufferSource, cameraPos);

        bufferSource.endBatch();
    }
}