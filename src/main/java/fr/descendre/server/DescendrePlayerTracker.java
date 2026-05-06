package fr.descendre.server;

import fr.descendre.network.ClientboundCubeDataPacket;
import fr.descendre.network.ClientboundForgetCubePacket;
import fr.descendre.network.DescendreNetwork;
import fr.descendre.world.cube.CubePos;
import fr.descendre.world.cube.DescendreCube;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pour chaque joueur, garde la liste des cubes actuellement "trackés" (= envoyés et que
 * le client connaît). Permet d'éviter les renvois redondants et d'envoyer les
 * "forget" quand le joueur s'éloigne d'un cube.
 *
 * Appelé par DescendreCubeTicker à chaque tick d'évaluation.
 */
public final class DescendrePlayerTracker {

    /** Pour chaque joueur (par UUID), l'ensemble des cubes dont il a connaissance. */
    private static final Map<UUID, Set<CubePos>> TRACKED = new ConcurrentHashMap<>();

    private DescendrePlayerTracker() {}

    /**
     * Synchronise un joueur avec le set de cubes qu'il devrait voir.
     *
     * - Pour chaque cube dans `desired` qui n'est pas déjà tracké : on envoie le CubeData
     *   et on l'ajoute au tracker.
     * - Pour chaque cube tracké qui n'est plus dans `desired` : on envoie un ForgetCube
     *   et on le retire du tracker.
     */
    public static void sync(ServerPlayer player, Set<CubePos> desired,
                            java.util.function.Function<CubePos, DescendreCube> cubeProvider) {
        Set<CubePos> tracked = TRACKED.computeIfAbsent(player.getUUID(), uuid -> new HashSet<>());

        // 1. Envoyer les nouveaux cubes
        for (CubePos pos : desired) {
            if (tracked.contains(pos)) continue;
            DescendreCube cube = cubeProvider.apply(pos);
            if (cube == null || cube.isEmpty()) {
                // Cube absent ou vide : on n'envoie rien et on NE marque PAS tracké.
                // Comme ça, si quelqu'un pose un bloc dedans plus tard, on l'enverra.
                continue;
            }
            PacketDistributor.sendToPlayer(player, ClientboundCubeDataPacket.fromCube(cube));

            tracked.add(pos);
        }

        // 2. Envoyer les forgets pour les cubes qui ne sont plus voulus
        Set<CubePos> toForget = new HashSet<>();
        for (CubePos pos : tracked) {
            if (!desired.contains(pos)) {
                toForget.add(pos);
            }
        }
        for (CubePos pos : toForget) {
            PacketDistributor.sendToPlayer(player, ClientboundForgetCubePacket.of(pos));
            tracked.remove(pos);
        }
    }

    /** Quand un joueur se déconnecte ou change de dimension. */
    public static void clearPlayer(UUID playerId) {
        TRACKED.remove(playerId);
    }

    /** Vide tout (ex: stop serveur). */
    public static void clearAll() {
        TRACKED.clear();
    }

    /** Retourne true si le joueur a connaissance de ce cube (= il l'a reçu). */
    public static boolean isTracking(ServerPlayer player, CubePos pos) {
        Set<CubePos> tracked = TRACKED.get(player.getUUID());
        return tracked != null && tracked.contains(pos);
    }
}