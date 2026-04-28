package fr.descendre;

import fr.descendre.command.DescendreCommands;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(DescendreMod.MODID)
public final class DescendreMod {
    public static final String MODID = "descendre";

    public DescendreMod(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.addListener(DescendreCommands::onRegisterCommands);
    }
}