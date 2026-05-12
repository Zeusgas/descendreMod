package fr.descendre.server;

import fr.descendre.cubic.DescendreServerConfig;
import fr.descendre.storage.CubeStorage;
import fr.descendre.world.cube.CubeMap;
import fr.descendre.world.cube.CubePos;
import fr.descendre.world.cube.DescendreCube;
import fr.descendre.worldgen.DescendreWorldGenerator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
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
        // Les scheduled ticks cubic doivent tourner tous les ticks serveur.
        for (ServerLevel level : levels) {
            DescendreScheduledTicks.get(level).tick(level);
        }

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

        // Pas de joueur connecté à cette dimension : rien à faire.
        if (level.players().isEmpty()) return;

        Set<CubePos> desired   = DescendreCubeTicketManager.computeDesired(level);
        Set<CubePos> keepAlive = DescendreCubeTicketManager.computeKeepAlive(level);

        // ---------- 1. Chargement RAM / génération ----------
        int loadBudget = DescendreServerConfig.maxLoadsPerTick();
        int attempts = 0;

        for (CubePos pos : desired) {
            if (attempts >= loadBudget) break;
            if (map.getCube(pos) != null) continue;

            attempts++;

            DescendreCube cube = storage.getCubeOrLoad(pos);

            if (cube == null
                    && DescendreWorldGenerator.isEnabledFor(level)
                    && DescendreWorldGenerator.mayContainGeneratedBlocks(pos)) {
                cube = DescendreWorldGenerator.generateCube(pos);
                if (cube != null) {
                    map.putCube(cube);
                }
            }

            if (cube != null) {
                cube.attachLevel(level);
            }
        }

        // ---------- 2. Synchronisation réseau ----------
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