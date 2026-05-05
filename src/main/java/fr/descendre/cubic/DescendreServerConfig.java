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

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("Paramètres du système de tickets cubic Descendre.").push("ticket_system");

        LOAD_RADIUS_CUBES = b
                .comment(
                        "Rayon horizontal de chargement autour du joueur, en cubes (1 cube = 16 blocs).",
                        "Tous les cubes dans un cylindre de ce rayon seront chargés en RAM.",
                        "Exemples : 8 = 128 blocs (léger), 16 = 256 blocs (confort), 32 = 512 blocs (lourd)."
                )
                .defineInRange("load_radius_cubes", 16, 1, 64);

        UNLOAD_RADIUS_CUBES = b
                .comment(
                        "Rayon au-delà duquel on décharge les cubes. Doit être >= load_radius_cubes.",
                        "Avoir une marge évite les cycles load/unload quand le joueur fait du va-et-vient."
                )
                .defineInRange("unload_radius_cubes", 20, 1, 96);

        VERTICAL_RADIUS_CUBES = b
                .comment(
                        "Rayon vertical de chargement, en cubes. Souvent plus grand que le rayon horizontal",
                        "puisque Descendre étend la hauteur. 32 cubes = 512 blocs verticaux autour du joueur."
                )
                .defineInRange("vertical_radius_cubes", 32, 1, 128);

        TICK_INTERVAL = b
                .comment(
                        "Fréquence du ticker, en ticks Minecraft (20 ticks = 1 seconde).",
                        "Plus c'est bas, plus c'est réactif aux mouvements du joueur, mais plus de CPU."
                )
                .defineInRange("tick_interval", 20, 1, 200);

        MAX_LOADS_PER_TICK = b
                .comment(
                        "Limite de cubes chargés depuis le disque par appel du ticker.",
                        "Évite les freezes quand le joueur tp loin et qu'il faudrait charger 1000 cubes d'un coup.",
                        "Les cubes restants seront chargés au tick suivant."
                )
                .defineInRange("max_loads_per_tick", 64, 1, 4096);

        MAX_UNLOADS_PER_TICK = b
                .comment(
                        "Limite de cubes déchargés (sauvegarde + retrait RAM) par appel du ticker."
                )
                .defineInRange("max_unloads_per_tick", 64, 1, 4096);

        b.pop();
        SPEC = b.build();
    }

    private DescendreServerConfig() {}

    // Helpers pratiques pour ne pas faire .get() partout
    public static int loadRadius()      { return LOAD_RADIUS_CUBES.get(); }
    public static int unloadRadius()    { return Math.max(UNLOAD_RADIUS_CUBES.get(), LOAD_RADIUS_CUBES.get() + 1); }
    public static int verticalRadius()  { return VERTICAL_RADIUS_CUBES.get(); }
    public static int tickInterval()    { return TICK_INTERVAL.get(); }
    public static int maxLoadsPerTick() { return MAX_LOADS_PER_TICK.get(); }
    public static int maxUnloadsPerTick(){ return MAX_UNLOADS_PER_TICK.get(); }
}