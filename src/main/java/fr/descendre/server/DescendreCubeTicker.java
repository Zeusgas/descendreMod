package fr.descendre.server;

import fr.descendre.cubic.DescendreServerConfig;
import fr.descendre.storage.CubeStorage;
import fr.descendre.world.cube.CubeMap;
import fr.descendre.world.cube.CubePos;
import fr.descendre.world.cube.DescendreCube;
import net.minecraft.server.level.ServerLevel;
import fr.descendre.server.DescendrePlayerTracker;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Tourne à chaque tick serveur (event ServerTickEvent).
 *
 * Tous les TICK_INTERVAL ticks, pour chaque ServerLevel :
 *   1. Calcule les cubes désirés (via DescendreCubeTicketManager)
 *   2. Charge depuis le disque ceux qui manquent en RAM (limite par tick)
 *   3. Décharge ceux qui sont hors keepAlive (limite par tick)
 */
public final class DescendreCubeTicker {

    /** Compteur de ticks par dimension pour respecter TICK_INTERVAL. */
    private static int tickCounter = 0;

    private DescendreCubeTicker() {}

    public static void onServerTick(Iterable<ServerLevel> levels) {
        tickCounter++;
        if (tickCounter < DescendreServerConfig.tickInterval()) return;
        tickCounter = 0;

        for (ServerLevel level : levels) {
            tickLevel(level);
        }
    }

    private static void tickLevel(ServerLevel level) {
        DescendreCubeManager.Entry entry = DescendreCubeManager.getEntry(level);
        CubeMap map = entry.map();
        CubeStorage storage = entry.storage();

        // Pas de joueur connecté à cette dimension : rien à faire (on garde la RAM telle quelle)
        if (level.players().isEmpty()) return;

        Set<CubePos> desired   = DescendreCubeTicketManager.computeDesired(level);
        Set<CubePos> keepAlive = DescendreCubeTicketManager.computeKeepAlive(level);

        // ---------- 1. Chargement RAM (depuis disque si nécessaire) ----------
        int loadBudget = DescendreServerConfig.maxLoadsPerTick();
        int loaded = 0;

        for (CubePos pos : desired) {
            if (loaded >= loadBudget) break;
            if (map.getCube(pos) != null) continue;
            DescendreCube cube = storage.getCubeOrLoad(pos);
            if (cube != null) loaded++;
        }

        // ---------- 2. Synchronisation réseau pour chaque joueur ----------
        for (ServerPlayer player : level.players()) {
            Set<CubePos> playerDesired = DescendreCubeTicketManager.computeDesiredForPlayer(player);
            DescendrePlayerTracker.sync(player, playerDesired, map::getCube);
        }

        // ---------- 3. Déchargement RAM ----------
        int unloadBudget = DescendreServerConfig.maxUnloadsPerTick();
        int unloaded = 0;

        List<DescendreCube> toUnload = new ArrayList<>();
        for (DescendreCube cube : map.allCubes()) {
            if (unloaded + toUnload.size() >= unloadBudget) break;
            if (!keepAlive.contains(cube.pos())) {
                toUnload.add(cube);
            }
        }

        for (DescendreCube cube : toUnload) {
            if (cube.isDirty()) {
                storage.saveCube(cube);
            }
            map.removeCube(cube.pos());
            unloaded++;
        }
    }
}