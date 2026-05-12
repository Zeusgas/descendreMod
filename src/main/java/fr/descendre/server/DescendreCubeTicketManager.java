package fr.descendre.server;

import fr.descendre.cubic.DescendreServerConfig;
import fr.descendre.world.cube.CubePos;
import fr.descendre.worldgen.DescendreWorldGenerator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Calcule les CubePos à charger autour des joueurs.
 *
 * Version optimisée pour Descendre : on ne demande plus un gros cylindre plein
 * sur des centaines de blocs de haut. On demande d'abord les cubes proches du
 * joueur, puis uniquement les cubes qui peuvent réellement contenir du terrain,
 * de l'arbre, des racines, des branches ou le dôme.
 */
public final class DescendreCubeTicketManager {

    private DescendreCubeTicketManager() {}

    /** Liste des cubes "désirés" autour des joueurs présents dans ce niveau. */
    public static Set<CubePos> computeDesired(ServerLevel level) {
        return computeForRadius(
                level,
                DescendreServerConfig.loadRadius(),
                DescendreServerConfig.verticalRadius(),
                false
        );
    }

    /** Liste plus large : cubes à garder vivants même s'ils ne sont plus "désirés". */
    public static Set<CubePos> computeKeepAlive(ServerLevel level) {
        return computeForRadius(
                level,
                DescendreServerConfig.unloadRadius(),
                DescendreServerConfig.verticalRadius() + 1,
                true
        );
    }

    private static Set<CubePos> computeForRadius(ServerLevel level, int radiusH, int radiusV, boolean keepAlive) {
        Set<CubePos> result = new LinkedHashSet<>();
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) return result;

        boolean descendre = DescendreWorldGenerator.isEnabledFor(level);
        for (ServerPlayer player : players) {
            if (descendre) {
                addOptimizedDescendreCubes(result, player, radiusH, radiusV, keepAlive);
            } else {
                addGenericCubes(result, player, radiusH, radiusV);
            }
        }
        return result;
    }

    /** Comme computeDesired, mais pour un seul joueur. */
    public static Set<CubePos> computeDesiredForPlayer(ServerPlayer player) {
        Set<CubePos> result = new LinkedHashSet<>();

        ServerLevel level = (ServerLevel) player.level();

        if (DescendreWorldGenerator.isEnabledFor(level)) {
            addOptimizedDescendreCubes(
                    result,
                    player,
                    DescendreServerConfig.loadRadius(),
                    DescendreServerConfig.verticalRadius(),
                    false
            );
        } else {
            addGenericCubes(
                    result,
                    player,
                    DescendreServerConfig.loadRadius(),
                    DescendreServerConfig.verticalRadius()
            );
        }

        return result;
    }

    /**
     * Chargement intelligent pour Descendre.
     *
     * - Rayon horizontal limité et ordonné du plus proche au plus loin.
     * - Rayon vertical proche volontairement plafonné : on ne charge plus 500 ou
     *   1000 blocs de hauteur juste parce que l'arbre fait 5000 blocs.
     * - Filtre worldgen avant d'ajouter la position, pour éviter les cubes vides.
     */
    private static void addOptimizedDescendreCubes(
            Set<CubePos> result,
            ServerPlayer player,
            int radiusH,
            int radiusV,
            boolean keepAlive
    ) {
        int playerCubeX = (int)Math.floor(player.getX()) >> 4;
        int playerCubeY = (int)Math.floor(player.getY()) >> 4;
        int playerCubeZ = (int)Math.floor(player.getZ()) >> 4;

        // Sécurité : même si un ancien fichier config contient encore 16/32,
        // Descendre garde une fenêtre raisonnable autour du joueur.
        radiusH = Math.min(radiusH, keepAlive ? 12 : 8);
        int radiusH2 = radiusH * radiusH;
        int nearVertical = Math.min(radiusV, DescendreServerConfig.nearVerticalRadius());
        if (keepAlive) {
            nearVertical += 1;
        }

        // 1) Priorité immédiate : cube joueur + sol sous/près du joueur.
        addIfGenerated(result, new CubePos(playerCubeX, playerCubeY, playerCubeZ));
        addIfGenerated(result, new CubePos(playerCubeX, 0, playerCubeZ));
        addIfGenerated(result, new CubePos(playerCubeX, -1, playerCubeZ));

        // 2) Anneaux horizontaux ordonnés : le centre se charge avant les bords.
        for (int ring = 0; ring <= radiusH; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    if (dx * dx + dz * dz > radiusH2) continue;

                    int cubeX = playerCubeX + dx;
                    int cubeZ = playerCubeZ + dz;

                    // Zone verticale proche du joueur, alternée : 0, -1, +1, -2, +2...
                    addVerticalBandOrdered(result, cubeX, playerCubeY, cubeZ, nearVertical);

                    // Tant qu'on joue près du sol, on force aussi les couches utiles du sol et des racines.
                    if (player.getY() < 220.0) {
                        addFixedYRange(result, cubeX, cubeZ, -3, 8);
                    }
                }
            }
        }
    }

    private static void addVerticalBandOrdered(Set<CubePos> result, int cubeX, int centerY, int cubeZ, int radiusV) {
        addIfGenerated(result, new CubePos(cubeX, centerY, cubeZ));
        for (int o = 1; o <= radiusV; o++) {
            addIfGenerated(result, new CubePos(cubeX, centerY - o, cubeZ));
            addIfGenerated(result, new CubePos(cubeX, centerY + o, cubeZ));
        }
    }

    private static void addFixedYRange(Set<CubePos> result, int cubeX, int cubeZ, int minY, int maxY) {
        for (int y = minY; y <= maxY; y++) {
            addIfGenerated(result, new CubePos(cubeX, y, cubeZ));
        }
    }

    private static void addIfGenerated(Set<CubePos> result, CubePos pos) {
        if (DescendreWorldGenerator.mayContainGeneratedBlocks(pos)) {
            result.add(pos);
        }
    }

    /** Fallback générique conservé pour les dimensions non-Descendre. */
    private static void addGenericCubes(Set<CubePos> result, ServerPlayer player, int radiusH, int radiusV) {
        int radiusH2 = radiusH * radiusH;

        int playerCubeX = (int)Math.floor(player.getX()) >> 4;
        int playerCubeY = (int)Math.floor(player.getY()) >> 4;
        int playerCubeZ = (int)Math.floor(player.getZ()) >> 4;

        for (int dx = -radiusH; dx <= radiusH; dx++) {
            for (int dz = -radiusH; dz <= radiusH; dz++) {
                if (dx * dx + dz * dz > radiusH2) continue;
                for (int dy = -radiusV; dy <= radiusV; dy++) {
                    result.add(new CubePos(playerCubeX + dx, playerCubeY + dy, playerCubeZ + dz));
                }
            }
        }
    }
}