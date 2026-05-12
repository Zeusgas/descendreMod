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
import net.minecraft.world.level.material.FluidState;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.level.BlockAndTintGetter;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DescendreCubeMesh {

    public record QuadEntry(int lx, int ly, int lz, BakedQuad quad, int tintColor, int packedLight) {}

    private record LightSource(int x, int y, int z, int light) {}

    public record FluidEntry(BlockPos worldPos, BlockState blockState, FluidState fluidState, int packedLight) {}

    private final List<FluidEntry> fluids;

    private static final Direction[] DIRECTIONS = Direction.values();

    private final CubePos cubePos;
    private final int originX;
    private final int originY;
    private final int originZ;
    private final Map<RenderType, List<QuadEntry>> quadsByType;

    private DescendreCubeMesh(
            CubePos cubePos,
            Map<RenderType, List<QuadEntry>> quadsByType,
            List<FluidEntry> fluids
    ) {
        this.cubePos = cubePos;
        this.originX = cubePos.x() << 4;
        this.originY = cubePos.y() << 4;
        this.originZ = cubePos.z() << 4;
        this.quadsByType = quadsByType;
        this.fluids = fluids;
    }

    public List<FluidEntry> fluids() {
        return fluids;
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
        if (!fluids.isEmpty()) {
            return false;
        }

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

        List<FluidEntry> fluids = new ArrayList<>();

        if (cube.isEmpty()) {
            return new DescendreCubeMesh(cubePos, result, fluids);
        }

        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();

        Minecraft minecraft = Minecraft.getInstance();
        BlockColors blockColors = minecraft.getBlockColors();
        BlockAndTintGetter tintGetter = minecraft.level;

        int worldOriginX = cubePos.x() << 4;
        int worldOriginY = cubePos.y() << 4;
        int worldOriginZ = cubePos.z() << 4;

        DescendreLightProbe lightProbe = new DescendreLightProbe(worldOriginX, worldOriginY, worldOriginZ);
        lightProbe.compute(cache);

        // DEBUG
        int sample0 = lightProbe.getPackedLight(worldOriginX + 8, worldOriginY + 8, worldOriginZ + 8);
        int sample1 = lightProbe.getPackedLight(worldOriginX + 8, worldOriginY + 15, worldOriginZ + 8);
        int skyTop = (sample1 >> 20) & 0xF;
        int skyMid = (sample0 >> 20) & 0xF;
        System.out.println("[LIGHT-DEBUG] cube=" + cubePos + " skyMid=" + skyMid + " skyTop=" + skyTop);

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

                    int packedLight = lightProbe.getPackedLight(worldX, worldY, worldZ);

                    FluidState fluidState = state.getFluidState();
                    if (fluidState != null && !fluidState.isEmpty()) {
                        fluids.add(new FluidEntry(
                                worldPos.immutable(),
                                state,
                                fluidState,
                                descendre$getPackedLight(tintGetter, worldPos)
                        ));
                    }

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

                        // Pour chaque face, lit la lumière du VOISIN dans la direction de la face.
                        // C'est ce que fait vanilla : la face up d'un tronc utilise la lumière du
                        // bloc au-dessus (skylight 15) au lieu de l'intérieur opaque du tronc (0).
                        int facePackedLight = lightProbe.getPackedLight(
                                neighborWorldPos.getX(),
                                neighborWorldPos.getY(),
                                neighborWorldPos.getZ()
                        );

                        for (BlockModelPart part : parts) {
                            for (BakedQuad quad : part.getQuads(direction)) {
                                bucket.add(new QuadEntry(
                                        lx,
                                        ly,
                                        lz,
                                        quad,
                                        descendre$getTintColor(blockColors, tintGetter, state, worldPos, quad),
                                        facePackedLight
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
                                    descendre$getTintColor(blockColors, tintGetter, state, worldPos, quad),
                                    packedLight
                            ));
                        }
                    }
                }
            }
        }

        return new DescendreCubeMesh(cubePos, result, fluids);
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


    private static int descendre$getPackedLight(BlockAndTintGetter tintGetter, BlockPos pos) {
        if (tintGetter == null) {
            return 0;
        }

        return LevelRenderer.getLightColor(tintGetter, pos);
    }

    private static List<LightSource> descendre$collectLightSources(
            DescendreClientCubeCache cache,
            int originX,
            int originY,
            int originZ
    ) {
        List<LightSource> lights = new ArrayList<>();
        BlockPos.MutableBlockPos scanPos = new BlockPos.MutableBlockPos();

        int minX = originX - 15;
        int minY = originY - 15;
        int minZ = originZ - 15;

        int maxX = originX + 30;
        int maxY = originY + 30;
        int maxZ = originZ + 30;

        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    scanPos.set(x, y, z);

                    BlockState state = cache.getBlock(scanPos);
                    if (state == null || state.isAir()) {
                        continue;
                    }

                    int light = descendre$getLightEmission(state);
                    if (light > 0) {
                        lights.add(new LightSource(x, y, z, light));
                    }
                }
            }
        }

        return lights;
    }

    private static int descendre$getPackedLight(BlockPos pos, List<LightSource> lightSources) {
        int blockLight = 0;

        for (LightSource source : lightSources) {
            int distance =
                    Math.abs(source.x - pos.getX())
                            + Math.abs(source.y - pos.getY())
                            + Math.abs(source.z - pos.getZ());

            if (distance > 15) {
                continue;
            }

            int value = source.light - distance;
            if (value > blockLight) {
                blockLight = value;
            }

            if (blockLight >= 15) {
                break;
            }
        }

        // Petite lumière ambiante pour éviter un noir total temporairement.
        blockLight = Math.max(1, Math.min(15, blockLight));

        // Pas de skylight vanilla dans les cubes Descendre pour l’instant.
        int skyLight = 0;

        return descendre$packLight(blockLight, skyLight);
    }

    private static int descendre$getLightEmission(BlockState state) {
        return Math.max(0, Math.min(15, state.getLightEmission()));
    }

    private static int descendre$packLight(int blockLight, int skyLight) {
        return ((blockLight & 15) << 4) | ((skyLight & 15) << 20);
    }



}