package fr.descendre.world.cube;

import net.minecraft.core.BlockPos;

public record CubePos(int x, int y, int z) {

    public static CubePos fromBlockPos(BlockPos pos) {
        return fromBlockCoords(pos.getX(), pos.getY(), pos.getZ());
    }

    public static CubePos fromBlockCoords(int blockX, int blockY, int blockZ) {
        return new CubePos(
                Math.floorDiv(blockX, 16),
                Math.floorDiv(blockY, 16),
                Math.floorDiv(blockZ, 16)
        );
    }

    public int minBlockX() {
        return x * 16;
    }

    public int minBlockY() {
        return y * 16;
    }

    public int minBlockZ() {
        return z * 16;
    }

    public static int localX(int blockX) {
        return Math.floorMod(blockX, 16);
    }

    public static int localY(int blockY) {
        return Math.floorMod(blockY, 16);
    }

    public static int localZ(int blockZ) {
        return Math.floorMod(blockZ, 16);
    }
}