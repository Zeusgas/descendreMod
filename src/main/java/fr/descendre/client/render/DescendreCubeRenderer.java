package fr.descendre.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.descendre.world.cube.CubePos;
import fr.descendre.world.cube.DescendreCube;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Dessine un DescendreCube en utilisant le BlockRenderDispatcher de Minecraft.
 *
 * Approche MVP J3 : on rend chaque bloc non-air via renderSingleBlock,
 * sans culling ni mesher. Lent mais simple. On optimisera en J5 (vrai mesher).
 */
public final class DescendreCubeRenderer {

    private DescendreCubeRenderer() {}

    /**
     * Rend un cube complet à sa position monde.
     *
     * @param poseStack pile de transformation (déjà translatée à camera-relative)
     * @param bufferSource où écrire les vertices
     * @param cube le cube à rendre
     */
    public static void renderCube(PoseStack poseStack, MultiBufferSource bufferSource, DescendreCube cube) {
        if (cube.isEmpty()) return;

        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        CubePos cubePos = cube.pos();

        // Coordonnées monde du coin (0,0,0) du cube
        int worldOriginX = cubePos.x() << 4;
        int worldOriginY = cubePos.y() << 4;
        int worldOriginZ = cubePos.z() << 4;

        // Pour chaque bloc local du cube
        for (int ly = 0; ly < 16; ly++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    BlockState state = cube.getLocal(lx, ly, lz);
                    if (state == null || state.isAir()) continue;

                    int worldX = worldOriginX + lx;
                    int worldY = worldOriginY + ly;
                    int worldZ = worldOriginZ + lz;
                    BlockPos worldPos = new BlockPos(worldX, worldY, worldZ);

                    poseStack.pushPose();
                    poseStack.translate(lx, ly, lz);

                    // Lumière forcée maximum (J3 = pas de propagation lumière)
                    int packedLight = 0x00F000F0; // sky 15, block 15
                    int packedOverlay = net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;

                    dispatcher.renderSingleBlock(
                            state,
                            poseStack,
                            bufferSource,
                            packedLight,
                            packedOverlay
                    );

                    poseStack.popPose();
                }
            }
        }
    }
}