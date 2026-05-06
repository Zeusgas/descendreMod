package fr.descendre.client.render;

import fr.descendre.client.DescendreClientCubeCache;
import fr.descendre.world.cube.CubePos;
import fr.descendre.world.cube.DescendreCube;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mesh pré-calculé d'un cube Descendre.
 *
 * Pour chaque RenderType (solid, cutout, translucent...), stocke la liste des
 * quads à émettre, déjà cullés et avec leur position locale (lx, ly, lz).
 *
 * À l'émission, on itère juste cette liste et on appelle putBulkData.
 * Plus aucune allocation, plus aucun cull, plus aucun model lookup par frame.
 */
public final class DescendreCubeMesh {

    /** Une entrée dans le mesh : un quad à une position locale donnée. */
    public record QuadEntry(int lx, int ly, int lz, BakedQuad quad) {}

    private static final Direction[] DIRECTIONS = Direction.values();

    /** Pour chaque RenderType, la liste des quads à émettre. */
    private final Map<RenderType, List<QuadEntry>> quadsByType;

    private DescendreCubeMesh(Map<RenderType, List<QuadEntry>> quadsByType) {
        this.quadsByType = quadsByType;
    }

    public Map<RenderType, List<QuadEntry>> quadsByType() {
        return quadsByType;
    }

    public boolean isEmpty() {
        return quadsByType.isEmpty();
    }

    /** Construit un mesh pour un cube en lisant aussi les voisins (pour le culling). */
    public static DescendreCubeMesh build(DescendreCube cube, DescendreClientCubeCache cache) {
        Map<RenderType, List<QuadEntry>> result = new HashMap<>();

        if (cube.isEmpty()) {
            return new DescendreCubeMesh(result);
        }

        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        CubePos cubePos = cube.pos();

        int worldOriginX = cubePos.x() << 4;
        int worldOriginY = cubePos.y() << 4;
        int worldOriginZ = cubePos.z() << 4;

        RandomSource random = RandomSource.create();
        BlockPos.MutableBlockPos worldPos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos neighborWorldPos = new BlockPos.MutableBlockPos();
        List<BlockModelPart> parts = new ArrayList<>();

        for (int ly = 0; ly < 16; ly++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    BlockState state = cube.getLocal(lx, ly, lz);
                    if (state == null || state.isAir()) continue;

                    int worldX = worldOriginX + lx;
                    int worldY = worldOriginY + ly;
                    int worldZ = worldOriginZ + lz;
                    worldPos.set(worldX, worldY, worldZ);

                    BlockStateModel stateModel = dispatcher.getBlockModel(state);
                    random.setSeed(state.getSeed(worldPos));

                    parts.clear();
                    stateModel.collectParts(random, parts);

                    RenderType renderType = ItemBlockRenderTypes.getRenderType(state);
                    List<QuadEntry> bucket = result.computeIfAbsent(renderType, t -> new ArrayList<>());

                    // Quads dirigés (avec face culling)
                    for (Direction dir : DIRECTIONS) {
                        neighborWorldPos.setWithOffset(worldPos, dir);
                        BlockState neighborState = cache.getBlock(neighborWorldPos);
                        if (!Block.shouldRenderFace(state, neighborState, dir)) continue;

                        for (BlockModelPart part : parts) {
                            for (BakedQuad quad : part.getQuads(dir)) {
                                bucket.add(new QuadEntry(lx, ly, lz, quad));
                            }
                        }
                    }

                    // Quads directionless
                    for (BlockModelPart part : parts) {
                        for (BakedQuad quad : part.getQuads(null)) {
                            bucket.add(new QuadEntry(lx, ly, lz, quad));
                        }
                    }
                }
            }
        }

        return new DescendreCubeMesh(result);
    }
}