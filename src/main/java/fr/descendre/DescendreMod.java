package fr.descendre;

import com.mojang.logging.LogUtils;
import fr.descendre.command.DescendreCommands;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(DescendreMod.MOD_ID)
public class DescendreMod {
    public static final String MOD_ID = "descendre";
    public static final Logger LOGGER = LogUtils.getLogger();

    public DescendreMod(IEventBus modEventBus) {
        LOGGER.info("Descendre NeoForge loading...");

        NeoForge.EVENT_BUS.register(DescendreCommands.class);

        LOGGER.info("Descendre NeoForge loaded.");
    }
}