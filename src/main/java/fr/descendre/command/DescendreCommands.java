package fr.descendre.command;

import com.mojang.brigadier.Command;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class DescendreCommands {
    private DescendreCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("descendre")
                        .then(Commands.literal("status")
                                .executes(context -> {
                                    context.getSource().sendSuccess(
                                            () -> Component.literal("Descendre NeoForge V1 fonctionne."),
                                            false
                                    );

                                    return Command.SINGLE_SUCCESS;
                                })
                        )

                        .then(Commands.literal("start")
                                .executes(context -> {
                                    var player = context.getSource().getPlayerOrException();

                                    player.teleportTo(
                                            0.5,
                                            200.0,
                                            0.5
                                    );

                                    context.getSource().sendSuccess(
                                            () -> Component.literal("Départ Descendre V1. Hauteur expérimentale désactivée pour l’instant."),
                                            false
                                    );

                                    return Command.SINGLE_SUCCESS;
                                })
                        )
        );
    }
}