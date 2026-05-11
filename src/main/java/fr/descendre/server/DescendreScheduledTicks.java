package fr.descendre.server;

import fr.descendre.world.cube.CubeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DescendreScheduledTicks {

    private static final Map<ServerLevel, DescendreScheduledTicks> INSTANCES = new HashMap<>();

    public static DescendreScheduledTicks get(ServerLevel level) {
        return INSTANCES.computeIfAbsent(level, l -> new DescendreScheduledTicks());
    }

    private record BlockEntry(BlockPos pos, Block block, long triggerTick) {}
    private record FluidEntry(BlockPos pos, Fluid fluid, long triggerTick) {}

    private final List<BlockEntry> blockEntries = new ArrayList<>();
    private final List<FluidEntry> fluidEntries = new ArrayList<>();

    private DescendreScheduledTicks() {}

    public void schedule(BlockPos pos, Block block, int delay, long currentGameTime) {
        BlockPos immutable = pos.immutable();
        long triggerTick = currentGameTime + delay;

        for (BlockEntry e : blockEntries) {
            if (e.pos.equals(immutable) && e.block == block && e.triggerTick == triggerTick) {
                return;
            }
        }

        blockEntries.add(new BlockEntry(immutable, block, triggerTick));
    }

    public void schedule(BlockPos pos, Fluid fluid, int delay, long currentGameTime) {
        BlockPos immutable = pos.immutable();
        long triggerTick = currentGameTime + delay;

        for (FluidEntry e : fluidEntries) {
            if (e.pos.equals(immutable) && e.fluid == fluid && e.triggerTick == triggerTick) {
                return;
            }
        }

        fluidEntries.add(new FluidEntry(immutable, fluid, triggerTick));
    }

    public void tick(ServerLevel level) {
        tickBlocks(level);
        tickFluids(level);
    }

    private void tickBlocks(ServerLevel level) {
        if (blockEntries.isEmpty()) return;

        long now = level.getGameTime();
        CubeMap map = DescendreCubeManager.get(level);

        int i = 0;
        while (i < blockEntries.size()) {
            BlockEntry entry = blockEntries.get(i);

            if (entry.triggerTick > now) {
                i++;
                continue;
            }

            blockEntries.remove(i);

            BlockState currentState = map.getBlock(entry.pos);
            if (currentState == null || currentState.getBlock() != entry.block) {
                continue;
            }

            try {
                currentState.tick(level, entry.pos, level.getRandom());
            } catch (Exception e) {
                System.err.println("[Descendre] Erreur block tick @ " + entry.pos + ": " + e.getMessage());
            }
        }
    }

    private void tickFluids(ServerLevel level) {
        if (fluidEntries.isEmpty()) return;

        long now = level.getGameTime();
        CubeMap map = DescendreCubeManager.get(level);

        int i = 0;
        while (i < fluidEntries.size()) {
            FluidEntry entry = fluidEntries.get(i);

            if (entry.triggerTick > now) {
                i++;
                continue;
            }

            fluidEntries.remove(i);

            BlockState currentState = map.getBlock(entry.pos);
            if (currentState == null) {
                continue;
            }

            FluidState fluidState = currentState.getFluidState();
            if (fluidState == null || fluidState.isEmpty() || fluidState.getType() != entry.fluid) {
                continue;
            }

            try {
                fluidState.tick(level, entry.pos, currentState);
            } catch (Exception e) {
                System.err.println("[Descendre] Erreur fluid tick @ " + entry.pos + ": " + e.getMessage());
            }
        }
    }

    public int pendingCount() {
        return blockEntries.size() + fluidEntries.size();
    }
}