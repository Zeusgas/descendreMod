package fr.descendre.server;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import fr.descendre.server.DescendreCubeTicker;
import fr.descendre.server.DescendrePlayerTracker;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Branche le cycle de vie de la persistance Descendre aux events NeoForge.
 *
 * Events utilisés :
 *  - LevelEvent.Save     : flush des cubes dirty quand Minecraft sauvegarde le monde
 *  - ServerStoppingEvent : sauvegarde finale + fermeture propre des fichiers
 */
public final class DescendreServerHooks {

    private DescendreServerHooks() {}

    public static void register(IEventBus modBus) {
        // Les events serveur passent par le bus global NeoForge, pas par le mod bus.
        NeoForge.EVENT_BUS.addListener(DescendreServerHooks::onLevelSave);
        NeoForge.EVENT_BUS.addListener(DescendreServerHooks::onServerStopping);
        NeoForge.EVENT_BUS.addListener(DescendreServerHooks::onServerTick);
        NeoForge.EVENT_BUS.addListener(DescendreServerHooks::onPlayerLogout);
    }

    private static void onLevelSave(LevelEvent.Save event) {
        // Cet event est appelé pour chaque dimension qui se sauvegarde.
        // On flush tout d'un coup ; c'est OK car flush est rapide si rien n'est dirty.
        DescendreCubeManager.flushAll();
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        DescendreCubeManager.closeAll();
        DescendrePlayerTracker.clearAll();
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        DescendreCubeTicker.onServerTick(server.getAllLevels());
    }

    private static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        DescendrePlayerTracker.clearPlayer(event.getEntity().getUUID());
    }
}