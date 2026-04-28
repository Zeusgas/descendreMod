package fr.descendre.world.cube;

import fr.descendre.core.DescendreHeight;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

import java.util.function.BiConsumer;
import java.util.HashMap;
import java.util.Map;

public final class CubeMap {
    private final Map<CubePos, DescendreCube> cubes = new HashMap<>();

    public BlockState getBlock(BlockPos pos) {
        if (!DescendreHeight.isInsideInternalRange(pos.getY())) {
            return Blocks.AIR.defaultBlockState();
        }

        CubePos cubePos = CubePos.fromBlockPos(pos);
        DescendreCube cube = cubes.get(cubePos);

        if (cube == null) {
            return Blocks.AIR.defaultBlockState();
        }

        return cube.getLocal(
                CubePos.localX(pos.getX()),
                CubePos.localY(pos.getY()),
                CubePos.localZ(pos.getZ())
        );
    }

    public void setBlock(BlockPos pos, BlockState state) {
        if (!DescendreHeight.isInsideInternalRange(pos.getY())) {
            throw new IllegalArgumentException(
                    "Y hors range Descendre: " + pos.getY() + ". Range interne: " + DescendreHeight.internalRangeText()
            );
        }

        CubePos cubePos = CubePos.fromBlockPos(pos);
        DescendreCube cube = cubes.computeIfAbsent(cubePos, DescendreCube::new);

        cube.setLocal(
                CubePos.localX(pos.getX()),
                CubePos.localY(pos.getY()),
                CubePos.localZ(pos.getZ()),
                state
        );

        if (cube.isEmpty()) {
            cubes.remove(cubePos);
        }
    }

    public boolean hasCube(CubePos pos) {
        return cubes.containsKey(pos);
    }

    public int loadedCubeCount() {
        return cubes.size();
    }

    public int totalNonAirBlocks() {
        int total = 0;

        for (DescendreCube cube : cubes.values()) {
            total += cube.nonAirCount();
        }

        return total;
    }

    public void clear() {
        cubes.clear();
    }

    public void forEachNonAirBlock(AABB box, BiConsumer<BlockPos, BlockState> consumer) {
        int minX = Mth.floor(box.minX);
        int minY = Mth.floor(box.minY);
        int minZ = Mth.floor(box.minZ);

        int maxX = Mth.floor(box.maxX);
        int maxY = Mth.floor(box.maxY);
        int maxZ = Mth.floor(box.maxZ);

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    mutable.set(x, y, z);

                    BlockState state = getBlock(mutable);

                    if (!state.isAir()) {
                        consumer.accept(mutable.immutable(), state);
                    }
                }
            }
        }
    }




}