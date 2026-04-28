package fr.descendre.world.cube;

import fr.descendre.core.DescendreConstants;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class DescendreCube {
    private final CubePos pos;

    // null = air, pour éviter de remplir 4096 cases avec AIR
    private final BlockState[] states = new BlockState[DescendreConstants.CUBE_VOLUME];

    private int nonAirCount = 0;
    private boolean dirty = false;

    public DescendreCube(CubePos pos) {
        this.pos = pos;
    }

    public CubePos pos() {
        return pos;
    }

    public BlockState getLocal(int localX, int localY, int localZ) {
        checkLocal(localX, localY, localZ);

        BlockState state = states[index(localX, localY, localZ)];
        return state == null ? Blocks.AIR.defaultBlockState() : state;
    }

    public void setLocal(int localX, int localY, int localZ, BlockState newState) {
        checkLocal(localX, localY, localZ);

        int index = index(localX, localY, localZ);
        BlockState oldState = states[index];

        boolean oldAir = oldState == null || oldState.isAir();
        boolean newAir = newState == null || newState.isAir();

        if (oldAir && !newAir) {
            nonAirCount++;
        } else if (!oldAir && newAir) {
            nonAirCount--;
        }

        states[index] = newAir ? null : newState;
        dirty = true;
    }

    public boolean isEmpty() {
        return nonAirCount == 0;
    }

    public int nonAirCount() {
        return nonAirCount;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markSaved() {
        dirty = false;
    }

    private static int index(int localX, int localY, int localZ) {
        return (localY << 8) | (localZ << 4) | localX;
    }

    private static void checkLocal(int localX, int localY, int localZ) {
        if (localX < 0 || localX >= 16 || localY < 0 || localY >= 16 || localZ < 0 || localZ >= 16) {
            throw new IllegalArgumentException(
                    "Coordonnée locale invalide: " + localX + ", " + localY + ", " + localZ
            );
        }
    }
}