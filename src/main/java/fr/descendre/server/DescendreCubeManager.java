package fr.descendre.server;

import fr.descendre.storage.CubeStorage;
import fr.descendre.world.cube.CubeMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gère un CubeMap + CubeStorage par dimension.
 *
 * - get(level)  : récupère (ou crée) la paire pour cette dimension
 * - flush(server) : flush sur disque toutes les dimensions
 * - closeAll(server) : sauvegarde + ferme proprement (stop serveur)
 */
public final class DescendreCubeManager {

    /** Une "entrée" par dimension : sa map RAM + son storage disque. */
    public record Entry(CubeMap map, CubeStorage storage) {}

    private static final Map<ResourceKey<Level>, Entry> LEVEL_ENTRIES = new ConcurrentHashMap<>();

    private DescendreCubeManager() {}

    /** Récupère (ou crée) l'entrée pour ce niveau. */
    public static Entry getEntry(ServerLevel level) {
        return LEVEL_ENTRIES.computeIfAbsent(level.dimension(), key -> {
            CubeMap map = new CubeMap();
            Path dir = resolveDimensionDir(level);
            CubeStorage storage = new CubeStorage(map, dir);

            // Quand un bloc change, on envoie une update aux joueurs qui voient ce changement
            map.setChangeListener((pos, state) -> {
                fr.descendre.world.cube.CubePos cp = fr.descendre.world.cube.CubePos.fromBlockPos(pos);
                for (net.minecraft.server.level.ServerPlayer player : level.players()) {
                    if (fr.descendre.server.DescendrePlayerTracker.isTracking(player, cp)) {
                        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                                player,
                                fr.descendre.network.ClientboundCubeBlockUpdatePacket.of(pos, state)
                        );
                    }
                }
            });

            return new Entry(map, storage);
        });
    }

    /** Raccourci : récupère juste le CubeMap. Conservé pour compatibilité avec le code existant. */
    public static CubeMap get(ServerLevel level) {
        return getEntry(level).map();
    }

    /** Raccourci : récupère juste le storage. */
    public static CubeStorage getStorage(ServerLevel level) {
        return getEntry(level).storage();
    }

    /** Vide la RAM (sans toucher au disque). Utilisé par /descendre cubic clear. */
    public static void clear(ServerLevel level) {
        Entry entry = LEVEL_ENTRIES.get(level.dimension());
        if (entry != null) {
            entry.map().clear();
        }
    }

    /** Flush sur disque pour toutes les dimensions du serveur. */
    public static void flushAll() {
        for (Entry entry : LEVEL_ENTRIES.values()) {
            entry.storage().flush();
        }
    }

    /** Stop propre : sauvegarde + ferme tous les fichiers. */
    public static void closeAll() {
        for (Entry entry : LEVEL_ENTRIES.values()) {
            entry.storage().close();
        }
        LEVEL_ENTRIES.clear();
    }

    /**
     * Calcule le dossier où stocker les fichiers .r3d pour cette dimension.
     * Pour overworld : world/descendre/overworld/
     * Pour the_nether : world/descendre/the_nether/
     */
    private static Path resolveDimensionDir(ServerLevel level) {
        MinecraftServer server = level.getServer();
        Path worldDir = server.getWorldPath(LevelResource.ROOT);
        Identifier dim = level.dimension().identifier();
        // Remplace ":" par "/" pour rester compatible filesystem
        String safeDim = dim.getNamespace() + "_" + dim.getPath();
        return worldDir.resolve("descendre").resolve(safeDim);
    }
}