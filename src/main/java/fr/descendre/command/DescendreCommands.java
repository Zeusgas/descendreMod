package fr.descendre.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import fr.descendre.core.DescendreHeight;
import fr.descendre.server.DescendreCubeManager;
import fr.descendre.world.cube.CubeMap;
import fr.descendre.world.cube.CubePos;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import fr.descendre.storage.DescendreCubeDiskStorage;
import java.io.IOException;

public final class DescendreCommands {
    private static final DynamicCommandExceptionType ERROR_BAD_Y =
            new DynamicCommandExceptionType(y -> Component.literal(
                    "Y hors range Descendre: " + y + ". Range interne: " + DescendreHeight.internalRangeText()
            ));

    private DescendreCommands() {}

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher(), event.getBuildContext());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        dispatcher.register(
                Commands.literal("descendre")
                        .then(Commands.literal("cubic")

                                .then(Commands.literal("info")
                                        .executes(ctx -> info(ctx.getSource()))
                                )

                                .then(Commands.literal("clear")
                                        .executes(ctx -> clear(ctx.getSource()))
                                )

                                .then(Commands.literal("save")
                                        .executes(ctx -> save(ctx.getSource()))
                                )

                                .then(Commands.literal("load")
                                        .executes(ctx -> load(ctx.getSource()))
                                )

                                .then(Commands.literal("tp")
                               //         .executes(ctx -> tp(ctx.getSource(), 0, -15000, 0))
                                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                                .executes(ctx -> tp(
                                                                        ctx.getSource(),
                                                                        IntegerArgumentType.getInteger(ctx, "x"),
                                                                        IntegerArgumentType.getInteger(ctx, "y"),
                                                                        IntegerArgumentType.getInteger(ctx, "z")
                                                                ))
                                                        )
                                                )
                                        )
                                )

                                .then(Commands.literal("get")
                                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                                .executes(ctx -> get(
                                                                        ctx.getSource(),
                                                                        IntegerArgumentType.getInteger(ctx, "x"),
                                                                        IntegerArgumentType.getInteger(ctx, "y"),
                                                                        IntegerArgumentType.getInteger(ctx, "z")
                                                                ))
                                                        )
                                                )
                                        )
                                )

                                .then(Commands.literal("set")
                                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                                .then(Commands.argument("block", BlockStateArgument.block(buildContext))
                                                                        .executes(ctx -> set(
                                                                                ctx.getSource(),
                                                                                IntegerArgumentType.getInteger(ctx, "x"),
                                                                                IntegerArgumentType.getInteger(ctx, "y"),
                                                                                IntegerArgumentType.getInteger(ctx, "z"),
                                                                                BlockStateArgument.getBlock(ctx, "block").getState()
                                                                        ))
                                                                )
                                                        )
                                                )
                                        )
                                )

                                .then(Commands.literal("platform")
                                        .then(Commands.argument("centerX", IntegerArgumentType.integer())
                                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                                        .then(Commands.argument("centerZ", IntegerArgumentType.integer())
                                                                .then(Commands.argument("radius", IntegerArgumentType.integer(0, 32))
                                                                        .then(Commands.argument("block", BlockStateArgument.block(buildContext))
                                                                                .executes(ctx -> platform(
                                                                                        ctx.getSource(),
                                                                                        IntegerArgumentType.getInteger(ctx, "centerX"),
                                                                                        IntegerArgumentType.getInteger(ctx, "y"),
                                                                                        IntegerArgumentType.getInteger(ctx, "centerZ"),
                                                                                        IntegerArgumentType.getInteger(ctx, "radius"),
                                                                                        BlockStateArgument.getBlock(ctx, "block").getState()
                                                                                ))
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
        );
    }

    private static int info(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        CubeMap map = DescendreCubeManager.get(level);

        source.sendSuccess(() -> Component.literal(
                "Descendre cubic actif"
                        + " | range cible: " + DescendreHeight.targetRangeText()
                        + " | range interne: " + DescendreHeight.internalRangeText()
                        + " | buildHeight réel Minecraft: " + level.getMinY() + " à " + level.getMaxY()
                        + " | cubes RAM: " + map.loadedCubeCount()
                        + " | blocs non-air: " + map.totalNonAirBlocks()
        ), false);

        return 1;
    }

    private static int clear(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        DescendreCubeManager.clear(level);

        source.sendSuccess(() -> Component.literal("Descendre cubic RAM vidée pour cette dimension."), true);
        return 1;
    }

    private static int tp(CommandSourceStack source, int x, int y, int z) throws CommandSyntaxException {
        checkY(y);

        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();

        if (!DescendreHeight.isInsideInternalRange(y)) {
            source.sendFailure(Component.literal("Y=" + y + " hors range Descendre: " + DescendreHeight.internalRangeText()));
            return 0;
        }

        int realBlocks = createRealPlatform(level, x, y, z, 5, Blocks.STONE.defaultBlockState());

        if (realBlocks <= 0) {
            source.sendFailure(Component.literal("La plateforme réelle n'a pas pu être créée."));
            return 0;
        }

        player.teleportTo(
                x + 0.5,
                y + 2.0,
                z + 0.5
        );

        final int finalRealBlocks = realBlocks;

        source.sendSuccess(() -> Component.literal(
                "Téléporté en " + x + " " + (y + 2) + " " + z
                        + " | plateforme réelle créée à Y=" + y
                        + " | blocs réels: " + finalRealBlocks
        ), true);

        return 1;
    }

    private static int get(CommandSourceStack source, int x, int y, int z) throws CommandSyntaxException {
        checkY(y);

        ServerLevel level = source.getLevel();
        CubeMap map = DescendreCubeManager.get(level);
        BlockPos pos = new BlockPos(x, y, z);

        BlockState state = map.getBlock(pos);

        System.out.println("[CMD get] pos=" + pos + " cube=" + CubePos.fromBlockPos(pos)
                + " result=" + BuiltInRegistries.BLOCK.getKey(state.getBlock())
                + " mapSize=" + map.loadedCubeCount());

        CubePos cubePos = CubePos.fromBlockPos(pos);

        source.sendSuccess(() -> Component.literal(
                "Bloc cubic en " + x + " " + y + " " + z
                        + " : " + BuiltInRegistries.BLOCK.getKey(state.getBlock())
                        + " | cube=" + cubePos
                        + " | local="
                        + CubePos.localX(x) + ","
                        + CubePos.localY(y) + ","
                        + CubePos.localZ(z)
        ), false);

        return 1;
    }

    private static int set(
            CommandSourceStack source,
            int x,
            int y,
            int z,
            BlockState state
    ) throws CommandSyntaxException {
        checkY(y);

        ServerLevel level = source.getLevel();
        CubeMap map = DescendreCubeManager.get(level);

        BlockPos pos = new BlockPos(x, y, z);
        map.setBlock(pos, state);

        System.out.println("[CMD set] pos=" + pos + " cube=" + CubePos.fromBlockPos(pos)
                + " mapSize=" + map.loadedCubeCount());


        CubePos cubePos = CubePos.fromBlockPos(pos);

        source.sendSuccess(() -> Component.literal(
                "Bloc cubic stocké en " + x + " " + y + " " + z
                        + " : " + BuiltInRegistries.BLOCK.getKey(state.getBlock())
                        + " | cube=" + cubePos
                        + " | local="
                        + CubePos.localX(x) + ","
                        + CubePos.localY(y) + ","
                        + CubePos.localZ(z)
        ), true);

        return 1;
    }

    private static int platform(
            CommandSourceStack source,
            int centerX,
            int y,
            int centerZ,
            int radius,
            BlockState state
    ) throws CommandSyntaxException {
        checkY(y);

        ServerLevel level = source.getLevel();
        CubeMap map = DescendreCubeManager.get(level);

        int cubicCount = 0;

        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                BlockPos pos = new BlockPos(x, y, z);
                map.setBlock(pos, state);
                cubicCount++;
            }
        }

        final int finalCubicCount = cubicCount;
        source.sendSuccess(() -> Component.literal(
                "Plateforme cubic stockée: " + finalCubicCount
                        + " blocs à Y=" + y
                        + " avec " + BuiltInRegistries.BLOCK.getKey(state.getBlock())
        ), true);

        return finalCubicCount;
    }

    private static void checkY(int y) throws CommandSyntaxException {
        if (!DescendreHeight.isInsideInternalRange(y)) {
            throw ERROR_BAD_Y.create(y);
        }
    }

    private static int createRealPlatform(
            ServerLevel level,
            int centerX,
            int y,
            int centerZ,
            int radius,
            BlockState state
    ) {
        if (DescendreHeight.isInsideInternalRange(y)) {
            System.out.println("[Descendre] Impossible de créer la plateforme réelle : Y=" + y
                    + " hors limites. min=" + level.getMinY()
                    + " max=" + level.getMaxY());
            return 0;
        }

        int count = 0;

        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                BlockPos pos = new BlockPos(x, y, z);
                level.setBlock(pos, state, 3);
                count++;
            }
        }

        System.out.println("[Descendre] Plateforme réelle créée à Y=" + y + " | blocs=" + count);
        return count;
    }

    private static int save(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        CubeMap map = DescendreCubeManager.get(level);

        try {
            int count = DescendreCubeDiskStorage.save(level, map);

            source.sendSuccess(() -> Component.literal(
                    "Descendre cubic sauvegardé : " + count + " blocs."
            ), true);

            return count;
        } catch (IOException e) {
            source.sendFailure(Component.literal(
                    "Erreur sauvegarde Descendre cubic : " + e.getMessage()
            ));

            return 0;
        }
    }

    private static int load(CommandSourceStack source) {
        ServerLevel level = source.getLevel();

        try {
            DescendreCubeManager.clear(level);

            CubeMap map = DescendreCubeManager.get(level);
            int count = DescendreCubeDiskStorage.load(level, map);

            source.sendSuccess(() -> Component.literal(
                    "Descendre cubic chargé : " + count + " blocs. Si le rendu ne se met pas à jour direct, quitte/reviens dans le monde."
            ), true);

            return count;
        } catch (IOException e) {
            source.sendFailure(Component.literal(
                    "Erreur chargement Descendre cubic : " + e.getMessage()
            ));

            return 0;
        }
    }

}