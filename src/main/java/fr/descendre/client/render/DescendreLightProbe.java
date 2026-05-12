package fr.descendre.client.render;

import fr.descendre.client.DescendreClientCubeCache;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Calcule skylight + blocklight pour tous les blocs d'un cube + padding 15.
 *
 * Distinction importante :
 *  - opacityBlock : combien le bloc atténue la lumière (BlockState.getLightBlock()).
 *    Feuilles = 1 (atténue de 1 par traversée), stone = 15 (bloque complètement).
 *  - canOcclude : bloc plein opaque (stone, dirt, ...) — bloque le skylight vertical descendant.
 *
 * Skylight propagation :
 *  - Top-down par colonne : skylight=15 jusqu'à rencontrer un bloc opaque (canOcclude).
 *    Si on rencontre un bloc semi-transparent (feuilles), on continue mais avec atténuation.
 *  - Propagation BFS horizontale : skylight - opacityBlock - 1 par traversée.
 *
 * Blocklight :
 *  - Depuis chaque émetteur (torche, lave), BFS avec atténuation -1 par bloc traversé
 *    + l'opacityBlock du bloc destination.
 */
public final class DescendreLightProbe {

    private static final int PADDING = 15;
    private static final int GRID_SIZE = 16 + 2 * PADDING;
    private static final int GRID_VOLUME = GRID_SIZE * GRID_SIZE * GRID_SIZE;

    private final int gridOriginX, gridOriginY, gridOriginZ;

    private final byte[] skyLight = new byte[GRID_VOLUME];
    private final byte[] blockLight = new byte[GRID_VOLUME];

    /** opacityBlock pour chaque case : 0 = transparent, 1-14 = semi, 15 = full opaque. */
    private final byte[] opacityBlock = new byte[GRID_VOLUME];
    /** true si bloc occlude le skylight vertical (stone, dirt, full blocks). */
    private final boolean[] occludesSky = new boolean[GRID_VOLUME];

    public DescendreLightProbe(int worldOriginX, int worldOriginY, int worldOriginZ) {
        this.gridOriginX = worldOriginX - PADDING;
        this.gridOriginY = worldOriginY - PADDING;
        this.gridOriginZ = worldOriginZ - PADDING;
    }

    public void compute(DescendreClientCubeCache cache) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        // === 1. Build opacity cache ===
        for (int gy = 0; gy < GRID_SIZE; gy++) {
            for (int gz = 0; gz < GRID_SIZE; gz++) {
                for (int gx = 0; gx < GRID_SIZE; gx++) {
                    int wx = gridOriginX + gx;
                    int wy = gridOriginY + gy;
                    int wz = gridOriginZ + gz;
                    pos.set(wx, wy, wz);
                    BlockState state = cache.getBlock(pos);
                    int idx = gridIndex(gx, gy, gz);
                    if (state == null || state.isAir() || isTransparentForLight(state)) {
                        opacityBlock[idx] = 0;
                        occludesSky[idx] = false;
                    } else {
                        int lightBlock = state.getLightBlock();
                        opacityBlock[idx] = (byte) Math.max(0, Math.min(15, lightBlock));
                        occludesSky[idx] = state.canOcclude() && lightBlock >= 15;
                    }
                }
            }
        }

        // === 2. Skylight initial : top-down par colonne, daylight commence à Y=0 ===
        // La lumière du ciel n'existe que pour Y >= 0. En dessous : noir total sauf
        // propagation horizontale depuis des zones éclairées.
        Deque<Integer> skyQueue = new ArrayDeque<>(8192);
        BlockPos.MutableBlockPos scanPos = new BlockPos.MutableBlockPos();

        // Pour debug : on log un seul cube
        boolean debugThisCube = (gridOriginX + PADDING) == 0 && (gridOriginZ + PADDING) == 0;

        for (int gz = 0; gz < GRID_SIZE; gz++) {
            for (int gx = 0; gx < GRID_SIZE; gx++) {
                int wx = gridOriginX + gx;
                int wz = gridOriginZ + gz;

                boolean blockedFromAbove = false;
                int topOfGridWorldY = gridOriginY + GRID_SIZE - 1;
                int scanLimit = topOfGridWorldY + 100;
                int blockerY = -99999;
                BlockState blocker = null;
                for (int wy = topOfGridWorldY + 1; wy <= scanLimit; wy++) {
                    scanPos.set(wx, wy, wz);
                    BlockState above = cache.getBlock(scanPos);
                    if (above != null && !above.isAir() && !isTransparentForLight(above)
                            && above.canOcclude() && above.getLightBlock() >= 15) {
                        blockedFromAbove = true;
                        blockerY = wy;
                        blocker = above;
                        break;
                    }
                }

                // Log central uniquement
                if (debugThisCube && gx == PADDING + 8 && gz == PADDING + 8) {
                    System.out.println("[LIGHT-PROBE] center column @ (" + wx + "," + wz + ") gridTopY=" + topOfGridWorldY
                            + " blocked=" + blockedFromAbove
                            + (blockedFromAbove ? " blockerY=" + blockerY + " blocker=" + blocker : ""));
                }

                for (int gy = GRID_SIZE - 1; gy >= 0; gy--) {
                    int idx = gridIndex(gx, gy, gz);

                    if (occludesSky[idx]) {
                        blockedFromAbove = true;
                    }

                    byte value;
                    if (blockedFromAbove) {
                        value = 0;
                    } else {
                        value = 15;
                    }

                    skyLight[idx] = value;
                    if (value > 0) {
                        skyQueue.push(idx);
                    }
                }
            }
        }

        // === 3. Propagation skylight horizontale ===
        propagateBFS(skyLight, skyQueue);

        // === 4. Blocklight : depuis chaque émetteur ===
        Deque<Integer> blockQueue = new ArrayDeque<>(1024);
        for (int gy = 0; gy < GRID_SIZE; gy++) {
            for (int gz = 0; gz < GRID_SIZE; gz++) {
                for (int gx = 0; gx < GRID_SIZE; gx++) {
                    int wx = gridOriginX + gx;
                    int wy = gridOriginY + gy;
                    int wz = gridOriginZ + gz;
                    pos.set(wx, wy, wz);
                    BlockState state = cache.getBlock(pos);
                    if (state == null || state.isAir()) continue;
                    int emission = state.getLightEmission();
                    if (emission > 0) {
                        int idx = gridIndex(gx, gy, gz);
                        blockLight[idx] = (byte) Math.min(15, emission);
                        blockQueue.push(idx);
                    }
                }
            }
        }
        propagateBFS(blockLight, blockQueue);
    }

    /**
     * BFS : propage vers les 6 voisins avec atténuation (au minimum -1, plus opacityBlock du voisin).
     */
    private void propagateBFS(byte[] grid, Deque<Integer> queue) {
        while (!queue.isEmpty()) {
            int idx = queue.poll();
            byte value = grid[idx];
            if (value <= 1) continue;

            int gx = idx % GRID_SIZE;
            int gy = (idx / GRID_SIZE) % GRID_SIZE;
            int gz = idx / (GRID_SIZE * GRID_SIZE);

            tryPropagate(grid, queue, gx - 1, gy, gz, value);
            tryPropagate(grid, queue, gx + 1, gy, gz, value);
            tryPropagate(grid, queue, gx, gy - 1, gz, value);
            tryPropagate(grid, queue, gx, gy + 1, gz, value);
            tryPropagate(grid, queue, gx, gy, gz - 1, value);
            tryPropagate(grid, queue, gx, gy, gz + 1, value);
        }
    }

    private void tryPropagate(byte[] grid, Deque<Integer> queue, int gx, int gy, int gz, byte sourceValue) {
        if (gx < 0 || gx >= GRID_SIZE || gy < 0 || gy >= GRID_SIZE || gz < 0 || gz >= GRID_SIZE) return;
        int idx = gridIndex(gx, gy, gz);
        // Atténuation : -1 minimum + opacité du bloc destination
        int loss = 1 + opacityBlock[idx];
        int propagated = sourceValue - loss;
        if (propagated <= 0) return;
        if (grid[idx] >= propagated) return;
        grid[idx] = (byte) propagated;
        queue.push(idx);
    }

    private static int gridIndex(int gx, int gy, int gz) {
        return (gz * GRID_SIZE + gy) * GRID_SIZE + gx;
    }

    public int getPackedLight(int worldX, int worldY, int worldZ) {
        int gx = worldX - gridOriginX;
        int gy = worldY - gridOriginY;
        int gz = worldZ - gridOriginZ;
        if (gx < 0 || gx >= GRID_SIZE || gy < 0 || gy >= GRID_SIZE || gz < 0 || gz >= GRID_SIZE) {
            return (15 << 20) | (15 << 4);
        }
        int idx = gridIndex(gx, gy, gz);
        int sky = skyLight[idx] & 0xF;
        int block = blockLight[idx] & 0xF;
        return (sky << 20) | (block << 4);
    }


    /**
     * Blocs qu'on traite comme totalement transparents pour la lumière,
     * même s'ils ont un getLightBlock > 0 en vanilla.
     */
    private static boolean isTransparentForLight(net.minecraft.world.level.block.state.BlockState state) {
        net.minecraft.world.level.block.Block block = state.getBlock();
        // Feuilles : font partie de plusieurs types (vanilla LeavesBlock + diverses sous-classes mod)
        if (block instanceof net.minecraft.world.level.block.LeavesBlock) return true;
        // Optionnel : autres blocs qu'on veut transparents
        // if (block instanceof net.minecraft.world.level.block.GlassBlock) return true;
        return false;
    }

}