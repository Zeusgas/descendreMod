package fr.descendre;

import fr.descendre.command.DescendreCommands;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import fr.descendre.server.DescendreServerHooks;
import fr.descendre.cubic.DescendreServerConfig;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import fr.descendre.network.DescendreNetwork;

@Mod(DescendreMod.MODID)
public final class DescendreMod {
    public static final String MODID = "descendre";

    public DescendreMod(IEventBus modEventBus, ModContainer container) {
        NeoForge.EVENT_BUS.addListener(DescendreCommands::onRegisterCommands);
        DescendreServerHooks.register(modEventBus);
        DescendreNetwork.register(modEventBus);
        container.registerConfig(ModConfig.Type.SERVER, DescendreServerConfig.SPEC);
    }
}