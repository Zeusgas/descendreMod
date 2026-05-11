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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.world.level.BlockAndTintGetter;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DescendreCubeMesh {

    public record QuadEntry(int lx, int ly, int lz, BakedQuad quad, int tintColor) {}

    private static final Direction[] DIRECTIONS = Direction.values();

    private final CubePos cubePos;
    private final int originX;
    private final int originY;
    private final int originZ;
    private final Map<RenderType, List<QuadEntry>> quadsByType;

    private DescendreCubeMesh(CubePos cubePos, Map<RenderType, List<QuadEntry>> quadsByType) {
        this.cubePos = cubePos;
        this.originX = cubePos.x() << 4;
        this.originY = cubePos.y() << 4;
        this.originZ = cubePos.z() << 4;
        this.quadsByType = quadsByType;
    }

    public CubePos cubePos() {
        return cubePos;
    }

    public int originX() {
        return originX;
    }

    public int originY() {
        return originY;
    }

    public int originZ() {
        return originZ;
    }

    public Map<RenderType, List<QuadEntry>> quadsByType() {
        return quadsByType;
    }

    public boolean isEmpty() {
        if (quadsByType.isEmpty()) {
            return true;
        }

        for (List<QuadEntry> entries : quadsByType.values()) {
            if (!entries.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    public static DescendreCubeMesh build(DescendreCube cube, DescendreClientCubeCache cache) {
        CubePos cubePos = cube.pos();
        Map<RenderType, List<QuadEntry>> result = new HashMap<>();

        if (cube.isEmpty()) {
            return new DescendreCubeMesh(cubePos, result);
        }

        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();

        Minecraft minecraft = Minecraft.getInstance();
        BlockColors blockColors = minecraft.getBlockColors();
        BlockAndTintGetter tintGetter = minecraft.level;

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

                    if (state == null || state.isAir()) {
                        continue;
                    }

                    int worldX = worldOriginX + lx;
                    int worldY = worldOriginY + ly;
                    int worldZ = worldOriginZ + lz;

                    worldPos.set(worldX, worldY, worldZ);

                    BlockStateModel stateModel = dispatcher.getBlockModel(state);

                    random.setSeed(state.getSeed(worldPos));
                    parts.clear();
                    stateModel.collectParts(random, parts);

                    RenderType renderType = ItemBlockRenderTypes.getRenderType(state);
                    List<QuadEntry> bucket = result.computeIfAbsent(renderType, ignored -> new ArrayList<>());

                    for (Direction direction : DIRECTIONS) {
                        neighborWorldPos.setWithOffset(worldPos, direction);

                        BlockState neighborState = cache.getBlock(neighborWorldPos);
                        if (neighborState == null) {
                            neighborState = Blocks.AIR.defaultBlockState();
                        }

                        if (!Block.shouldRenderFace(state, neighborState, direction)) {
                            continue;
                        }

                        for (BlockModelPart part : parts) {
                            for (BakedQuad quad : part.getQuads(direction)) {
                                bucket.add(new QuadEntry(
                                    lx,
                                    ly,
                                    lz,
                                    quad,
                                    descendre$getTintColor(blockColors, tintGetter, state, worldPos, quad)
                            ));
                            }
                        }
                    }

                    for (BlockModelPart part : parts) {
                        for (BakedQuad quad : part.getQuads(null)) {
                            bucket.add(new QuadEntry(
                                    lx,
                                    ly,
                                    lz,
                                    quad,
                                    descendre$getTintColor(blockColors, tintGetter, state, worldPos, quad)
                            ));
                        }
                    }
                }
            }
        }

        return new DescendreCubeMesh(cubePos, result);
    }

    private static int descendre$getTintColor(
            BlockColors blockColors,
            BlockAndTintGetter tintGetter,
            BlockState state,
            BlockPos pos,
            BakedQuad quad
    ) {
        if (!quad.isTinted()) {
            return 0xFFFFFF;
        }

        if (tintGetter == null) {
            return 0xFFFFFF;
        }

        int color = blockColors.getColor(state, tintGetter, pos, quad.tintIndex());

        if (color == -1) {
            return 0xFFFFFF;
        }

        return color;
    }




}