package fr.descendre.cubic;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Config serveur Descendre, exposée dans config/descendre-server.toml.
 *
 * Toutes les valeurs sont rechargées à chaud quand le fichier change,
 * sauf indication contraire.
 */
public final class DescendreServerConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue LOAD_RADIUS_CUBES;
    public static final ModConfigSpec.IntValue UNLOAD_RADIUS_CUBES;
    public static final ModConfigSpec.IntValue TICK_INTERVAL;
    public static final ModConfigSpec.IntValue MAX_LOADS_PER_TICK;
    public static final ModConfigSpec.IntValue MAX_UNLOADS_PER_TICK;
    public static final ModConfigSpec.IntValue VERTICAL_RADIUS_CUBES;
    public static final ModConfigSpec.IntValue NEAR_VERTICAL_RADIUS_CUBES;
    public static final ModConfigSpec.IntValue MAX_SYNC_PACKETS_PER_PLAYER;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("Paramètres du système de tickets cubic Descendre.").push("ticket_system");

        LOAD_RADIUS_CUBES = b
                .comment(
                        "Rayon horizontal de chargement autour du joueur, en cubes (1 cube = 16 blocs).",
                        "Tous les cubes dans un cylindre de ce rayon seront considérés, mais Descendre filtre ensuite les cubes vides.",
                        "8 = 128 blocs, bon compromis pour éviter de saturer le rendu."
                )
                .defineInRange("load_radius_cubes", 8, 1, 64);

        UNLOAD_RADIUS_CUBES = b
                .comment(
                        "Rayon au-delà duquel on décharge les cubes. Doit être >= load_radius_cubes.",
                        "Avoir une petite marge évite les cycles load/unload quand le joueur fait du va-et-vient."
                )
                .defineInRange("unload_radius_cubes", 10, 1, 96);

        VERTICAL_RADIUS_CUBES = b
                .comment(
                        "Rayon vertical théorique de chargement, en cubes.",
                        "Ancienne valeur trop lourde : 32 chargeait 512 blocs au-dessus/dessous du joueur.",
                        "6 = 96 blocs verticaux maximum autour du joueur."
                )
                .defineInRange("vertical_radius_cubes", 6, 1, 128);

        NEAR_VERTICAL_RADIUS_CUBES = b
                .comment(
                        "Rayon vertical réellement scanné autour du joueur dans Descendre, en cubes.",
                        "Le monde peut faire 5000 blocs de haut, mais on ne doit pas tout charger autour du joueur.",
                        "4 = 64 blocs au-dessus/dessous, 6 = 96 blocs, 10 = 160 blocs."
                )
                .defineInRange("near_vertical_radius_cubes", 4, 1, 32);

        TICK_INTERVAL = b
                .comment(
                        "Fréquence du ticker, en ticks Minecraft (20 ticks = 1 seconde).",
                        "2 = réactif sans charger une énorme zone d'un coup."
                )
                .defineInRange("tick_interval", 2, 1, 200);

        MAX_LOADS_PER_TICK = b
                .comment(
                        "Limite de cubes essayés/chargés par appel du ticker.",
                        "Ce budget limite aussi les tentatives sur des cubes procéduraux finalement vides."
                )
                .defineInRange("max_loads_per_tick", 32, 1, 4096);

        MAX_UNLOADS_PER_TICK = b
                .comment(
                        "Limite de cubes déchargés (sauvegarde + retrait RAM) par appel du ticker."
                )
                .defineInRange("max_unloads_per_tick", 64, 1, 4096);

        MAX_SYNC_PACKETS_PER_PLAYER = b
                .comment(
                        "Nombre maximum de cubes envoyés au client par joueur et par appel du ticker.",
                        "Évite de saturer le réseau et le renderer quand une grosse zone apparaît d'un coup."
                )
                .defineInRange("max_sync_packets_per_player", 48, 1, 1024);

        b.pop();
        SPEC = b.build();
    }

    private DescendreServerConfig() {}

    // Helpers pratiques pour ne pas faire .get() partout
    public static int loadRadius()      { return LOAD_RADIUS_CUBES.get(); }
    public static int unloadRadius()    { return Math.max(UNLOAD_RADIUS_CUBES.get(), LOAD_RADIUS_CUBES.get() + 1); }
    public static int verticalRadius()  { return VERTICAL_RADIUS_CUBES.get(); }
    public static int nearVerticalRadius() { return Math.min(NEAR_VERTICAL_RADIUS_CUBES.get(), verticalRadius()); }
    public static int tickInterval()    { return TICK_INTERVAL.get(); }
    public static int maxLoadsPerTick() { return MAX_LOADS_PER_TICK.get(); }
    public static int maxUnloadsPerTick(){ return MAX_UNLOADS_PER_TICK.get(); }
    public static int maxSyncPacketsPerPlayer(){ return MAX_SYNC_PACKETS_PER_PLAYER.get(); }
}