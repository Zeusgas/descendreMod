package fr.descendre.server;

import fr.descendre.cubic.DescendreServerConfig;
import fr.descendre.world.cube.CubePos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Calcule l'ensemble des CubePos qui doivent être chargés en RAM dans une dimension,
 * en se basant sur la position de chaque joueur connecté à cette dimension.
 *
 * On calcule deux ensembles :
 *   - desired   : les cubes à avoir en RAM (rayon de chargement)
 *   - keepAlive : les cubes à NE PAS décharger (rayon plus large, pour éviter le yo-yo)
 *
 * Chargement effectif et déchargement sont gérés par DescendreCubeTicker.
 */
public final class DescendreCubeTicketManager {

    private DescendreCubeTicketManager() {}

    /** Liste des cubes "désirés" autour des joueurs présents dans ce niveau. */
    public static Set<CubePos> computeDesired(ServerLevel level) {
        return computeForRadius(
                level,
                DescendreServerConfig.loadRadius(),
                DescendreServerConfig.verticalRadius()
        );
    }

    /** Liste plus large : cubes à garder vivants même s'ils ne sont plus "désirés". */
    public static Set<CubePos> computeKeepAlive(ServerLevel level) {
        return computeForRadius(
                level,
                DescendreServerConfig.unloadRadius(),
                DescendreServerConfig.verticalRadius() + 2
        );
    }

    private static Set<CubePos> computeForRadius(ServerLevel level, int radiusH, int radiusV) {
        Set<CubePos> result = new HashSet<>();
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) return result;

        int radiusH2 = radiusH * radiusH; // pour test cylindrique sans sqrt

        for (ServerPlayer player : players) {
            int playerCubeX = (int) Math.floor(player.getX()) >> 4;
            int playerCubeY = (int) Math.floor(player.getY()) >> 4;
            int playerCubeZ = (int) Math.floor(player.getZ()) >> 4;

            for (int dx = -radiusH; dx <= radiusH; dx++) {
                for (int dz = -radiusH; dz <= radiusH; dz++) {
                    if (dx * dx + dz * dz > radiusH2) continue; // cylindre, pas cube
                    for (int dy = -radiusV; dy <= radiusV; dy++) {
                        result.add(new CubePos(
                                playerCubeX + dx,
                                playerCubeY + dy,
                                playerCubeZ + dz
                        ));
                    }
                }
            }
        }
        return result;
    }
}