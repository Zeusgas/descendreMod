package fr.descendre.world.cube;

import fr.descendre.core.DescendreHeight;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import fr.descendre.server.DescendreScheduledTicks;
import net.minecraft.world.level.block.FallingBlock;
import fr.descendre.server.DescendreScheduledTicks;
import net.minecraft.core.Direction;
import net.minecraft.world.level.material.FluidState;

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

        if (changeListener != null) {
            changeListener.accept(pos.immutable(), state);
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

    /** Retourne le cube à cette position, ou null s'il n'est pas en RAM. */
    public DescendreCube getCube(CubePos pos) {
        return cubes.get(pos);
    }

    /** Place un cube en RAM (utilisé par le chargement disque). */
    public void putCube(DescendreCube cube) {
        if (!cube.isEmpty()) {
            cubes.put(cube.pos(), cube);
        }
    }

    /** Retire un cube de la RAM (sans le sauvegarder). */
    public void removeCube(CubePos pos) {
        cubes.remove(pos);
    }

    /** Itérable sur tous les cubes en RAM (pour la sauvegarde). */
    public Iterable<DescendreCube> allCubes() {
        return cubes.values();
    }

    /** Callback appelé après chaque setBlock. Optionnel. */
    private java.util.function.BiConsumer<BlockPos, BlockState> changeListener;

    public void setChangeListener(java.util.function.BiConsumer<BlockPos, BlockState> listener) {
        this.changeListener = listener;
    }

    /**
     * Variante serveur de setBlock qui gère aussi la création/destruction des BlockEntity
     * pour les blocs implémentant EntityBlock.
     */
    public void setBlockServer(BlockPos pos, BlockState state, ServerLevel level) {
        setBlockServerInternal(pos, state, level, true);
    }

    private void setBlockServerInternal(BlockPos pos, BlockState state, ServerLevel level, boolean triggerNeighborUpdates) {
        CubePos cubePos = CubePos.fromBlockPos(pos);
        DescendreCube cube = cubes.computeIfAbsent(cubePos, p -> new DescendreCube(p));
        BlockPos immutable = pos.immutable();

        cube.setLocalServer(
                CubePos.localX(pos.getX()),
                CubePos.localY(pos.getY()),
                CubePos.localZ(pos.getZ()),
                state,
                level,
                immutable
        );

        if (cube.isEmpty()) {
            cubes.remove(cubePos);
        }

        // CRITIQUE : notifie le client pour qu'il mette à jour son cache
        if (changeListener != null) {
            changeListener.accept(immutable, state);
        }

        if (immutable.getY() < -1000) {
            System.out.println("[SERVER-CHANGE] pos=" + immutable + " state=" + state + " trigger=" + triggerNeighborUpdates);
        }

        if (triggerNeighborUpdates) {
            descendre$scheduleFluidsAround(level, immutable, state);
            descendre$scheduleFallingBlocksAround(level, immutable, state);
        }

        // Propage updateShape aux 6 voisins (pour les blocs interconnectés : portes, coffres, barrières)
        if (triggerNeighborUpdates) {
            for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                net.minecraft.core.BlockPos neighborPos = immutable.relative(dir);
                BlockState neighborState = getBlock(neighborPos);
                if (neighborState == null || neighborState.isAir()) continue;

                // 1. updateShape : pour les connexions visuelles (portes, barrières, coffres)
                BlockState updated = neighborState.updateShape(
                        level, level, neighborPos, dir.getOpposite(), immutable, state, level.getRandom()
                );
                if (updated != neighborState) {
                    setBlockServerInternal(neighborPos, updated, level, false);
                    neighborState = updated; // important pour le neighborChanged ci-dessous
                }

                // 2. neighborChanged : pour les comportements (sand qui tombe, plantes, redstone)
                try {
                    neighborState.handleNeighborChanged(level, neighborPos, state.getBlock(), null, false);
                } catch (Exception e) {
                    System.err.println("[Descendre] handleNeighborChanged @ " + neighborPos + ": " + e.getMessage());
                }
            }
        }
    }

    /**
     * Retourne le BlockEntity à cette position, ou null s'il n'y en a pas
     * ou si le cube n'est pas chargé.
     */
    public BlockEntity getBlockEntity(BlockPos pos) {
        DescendreCube cube = cubes.get(CubePos.fromBlockPos(pos));
        if (cube == null) return null;
        return cube.getBlockEntityLocal(
                CubePos.localX(pos.getX()),
                CubePos.localY(pos.getY()),
                CubePos.localZ(pos.getZ())
        );
    }

    private void descendre$scheduleFallingBlocksAround(ServerLevel level, BlockPos changedPos, BlockState newState) {
        // Cas 1 : on vient de poser un falling block avec de l'air dessous.
        if (newState.getBlock() instanceof FallingBlock && descendre$canFallThrough(level, changedPos.below())) {
            DescendreScheduledTicks.get(level).schedule(
                    changedPos,
                    newState.getBlock(),
                    2,
                    level.getGameTime()
            );
        }

        // Cas 2 : on vient de casser/remplacer le bloc sous du sable/gravier/etc.
        BlockPos abovePos = changedPos.above();
        BlockState aboveState = getBlock(abovePos);

        if (aboveState != null
                && aboveState.getBlock() instanceof FallingBlock
                && descendre$canFallThrough(level, changedPos)) {
            DescendreScheduledTicks.get(level).schedule(
                    abovePos,
                    aboveState.getBlock(),
                    2,
                    level.getGameTime()
            );
        }
    }

    private boolean descendre$canFallThrough(ServerLevel level, BlockPos pos) {
        BlockState state = getBlock(pos);
        return state == null
                || state.isAir()
                || state.getCollisionShape(level, pos).isEmpty();
    }

    private void descendre$scheduleFluidsAround(ServerLevel level, BlockPos changedPos, BlockState newState) {
        descendre$scheduleFluidIfPresent(level, changedPos, newState);

        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = changedPos.relative(direction);
            BlockState neighborState = getBlock(neighborPos);
            descendre$scheduleFluidIfPresent(level, neighborPos, neighborState);
        }
    }

    private void descendre$scheduleFluidIfPresent(ServerLevel level, BlockPos pos, BlockState state) {
        if (state == null) {
            return;
        }

        FluidState fluidState = state.getFluidState();
        if (fluidState == null || fluidState.isEmpty()) {
            return;
        }

        int delay = Math.max(1, fluidState.getType().getTickDelay(level));

        DescendreScheduledTicks.get(level).schedule(
                pos,
                fluidState.getType(),
                delay,
                level.getGameTime()
        );
    }




}