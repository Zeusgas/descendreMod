package fr.descendre.extended;

import net.minecraft.server.level.ServerLevel;

public final class ExtendedY {
    public static final int CUBE_SIZE = 16;

    public static final int MIN_Y = -5008;
    public static final int MAX_Y = 15007;

    private ExtendedY() {
    }

    public static boolean isAllowed(int y) {
        return y >= MIN_Y && y <= MAX_Y;
    }

    public static boolean isVanillaBuildHeight(ServerLevel level, int y) {
        return !level.isOutsideBuildHeight(y);
    }

    public static int cubeCoord(int blockCoord) {
        return Math.floorDiv(blockCoord, CUBE_SIZE);
    }
}