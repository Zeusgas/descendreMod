package fr.descendre.server;

import fr.descendre.world.cube.CubeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Maintient et exécute des ticks programmés pour des blocs cubic (Y hors range vanilla).
 *
 * Vanilla LevelTicks rejette les scheduleTick pour les positions "not loaded".
 * Nos positions cubic à Y=-10000 sont considérées non chargées par LevelTicks → on doit
 * notre propre système.
 *
 * Une entrée par (position, block) : on stocke le tick d'exécution.
 * À chaque tick serveur, on parcourt et déclenche Block.tick() pour les entrées arrivées
 * à échéance.
 */
public final class DescendreScheduledTicks {

    private static final Map<ServerLevel, DescendreScheduledTicks> INSTANCES = new HashMap<>();

    public static DescendreScheduledTicks get(ServerLevel level) {
        return INSTANCES.computeIfAbsent(level, l -> new DescendreScheduledTicks());
    }

    private record Entry(BlockPos pos, Block block, long triggerTick) {}

    private final List<Entry> entries = new ArrayList<>();

    private DescendreScheduledTicks() {}

    /** Programme un tick pour {pos, block} à `gameTime + delay`. */
    public void schedule(BlockPos pos, Block block, int delay, long currentGameTime) {
        BlockPos immutable = pos.immutable();
        long triggerTick = currentGameTime + delay;

        // Évite les doublons exacts (mêmes pos+block+tick)
        for (Entry e : entries) {
            if (e.pos.equals(immutable) && e.block == block && e.triggerTick == triggerTick) return;
        }
        entries.add(new Entry(immutable, block, triggerTick));
    }

    /** À appeler chaque tick serveur. Exécute les ticks arrivés à échéance. */
    public void tick(ServerLevel level) {
        if (entries.isEmpty()) return;

        long now = level.getGameTime();
        CubeMap map = DescendreCubeManager.get(level);

        // On itère avec un index pour pouvoir supprimer en place
        int i = 0;
        while (i < entries.size()) {
            Entry entry = entries.get(i);
            if (entry.triggerTick > now) {
                i++;
                continue;
            }

            // Tick arrivé à échéance : on l'enlève de la liste avant de le déclencher
            entries.remove(i);

            BlockState currentState = map.getBlock(entry.pos);
            if (currentState == null || currentState.getBlock() != entry.block) {
                // Le bloc a changé depuis le schedule : on ignore
                continue;
            }

            try {
                currentState.tick(level, entry.pos, level.getRandom());
            } catch (Exception e) {
                System.err.println("[Descendre] Erreur tick @ " + entry.pos + ": " + e.getMessage());
            }
        }
    }

    public int pendingCount() {
        return entries.size();
    }
}