package extended_backup;

import net.minecraft.core.BlockPos;

public record CubePos(int x, int y, int z) {
    public static CubePos fromBlockPos(BlockPos pos) {
        return new CubePos(
                ExtendedY.cubeCoord(pos.getX()),
                ExtendedY.cubeCoord(pos.getY()),
                ExtendedY.cubeCoord(pos.getZ())
        );
    }

    public String storageKey() {
        return x + "," + y + "," + z;
    }
}