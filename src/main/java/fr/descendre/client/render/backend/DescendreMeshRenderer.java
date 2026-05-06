package fr.descendre.client.render.backend;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.descendre.client.render.DescendreCubeMesh;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

public interface DescendreMeshRenderer {
    void render(
            DescendreCubeMesh cubeMesh,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            Vec3 cameraPos
    );
}