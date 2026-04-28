package fr.descendre.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import fr.descendre.extended.CubePos;
import fr.descendre.extended.ExtendedBlockStore;
import fr.descendre.extended.ExtendedY;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class DescendreCommands {
    private DescendreCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("descendre")

                        .then(Commands.literal("set")
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("y", IntegerArgumentType.integer())
                                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                                        .then(Commands.argument("block", BlockStateArgument.block(event.getBuildContext()))
                                                                .executes(ctx -> setExtendedBlock(
                                                                        ctx,
                                                                        IntegerArgumentType.getInteger(ctx, "x"),
                                                                        IntegerArgumentType.getInteger(ctx, "y"),
                                                                        IntegerArgumentType.getInteger(ctx, "z"),
                                                                        BlockStateArgument.getBlock(ctx, "block")
                                                                ))
                                                        )
                                                )
                                        )
                                )
                        )

                        .then(Commands.literal("get")
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("y", IntegerArgumentType.integer())
                                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                                        .executes(ctx -> getExtendedBlock(
                                                                ctx,
                                                                IntegerArgumentType.getInteger(ctx, "x"),
                                                                IntegerArgumentType.getInteger(ctx, "y"),
                                                                IntegerArgumentType.getInteger(ctx, "z")
                                                        ))
                                                )
                                        )
                                )
                        )

                        .then(Commands.literal("cube")
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("y", IntegerArgumentType.integer())
                                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                                        .executes(ctx -> getCube(
                                                                ctx,
                                                                IntegerArgumentType.getInteger(ctx, "x"),
                                                                IntegerArgumentType.getInteger(ctx, "y"),
                                                                IntegerArgumentType.getInteger(ctx, "z")
                                                        ))
                                                )
                                        )
                                )
                        )

                        .then(Commands.literal("stats")
                                .executes(DescendreCommands::stats)
                        )
        );
    }

    private static int setExtendedBlock(CommandContext<CommandSourceStack> ctx, int x, int y, int z, BlockInput blockInput) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();

        if (!ExtendedY.isAllowed(y)) {
            source.sendFailure(Component.literal(
                    "Y refusé. Plage Descendre autorisée : " + ExtendedY.MIN_Y + " à " + ExtendedY.MAX_Y
            ));
            return 0;
        }

        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = blockInput.getState();

        if (ExtendedY.isVanillaBuildHeight(level, y)) {
            boolean success = level.setBlock(pos, state, 3);

            String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();

            source.sendSuccess(() -> Component.literal(
                    "Bloc vanilla placé en " + formatPos(pos) + " : " + blockId
            ), true);

            return success ? 1 : 0;
        }

        ExtendedBlockStore store = ExtendedBlockStore.get(source.getServer());

        if (state.isAir()) {
            store.remove(level, pos);
            store.saveIfDirty();

            source.sendSuccess(() -> Component.literal(
                    "Bloc extended supprimé en " + formatPos(pos)
            ), true);

            return 1;
        }

        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();

        store.set(level, pos, blockId);
        store.saveIfDirty();

        source.sendSuccess(() -> Component.literal(
                "Bloc EXTENDED stocké en " + formatPos(pos) + " : " + blockId
                        + " | cube=" + CubePos.fromBlockPos(pos).storageKey()
        ), true);

        return 1;
    }

    private static int getExtendedBlock(CommandContext<CommandSourceStack> ctx, int x, int y, int z) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();

        if (!ExtendedY.isAllowed(y)) {
            source.sendFailure(Component.literal(
                    "Y refusé. Plage Descendre autorisée : " + ExtendedY.MIN_Y + " à " + ExtendedY.MAX_Y
            ));
            return 0;
        }

        BlockPos pos = new BlockPos(x, y, z);

        if (ExtendedY.isVanillaBuildHeight(level, y)) {
            BlockState state = level.getBlockState(pos);
            String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();

            source.sendSuccess(() -> Component.literal(
                    "Bloc vanilla en " + formatPos(pos) + " : " + blockId
            ), false);

            return 1;
        }

        ExtendedBlockStore store = ExtendedBlockStore.get(source.getServer());

        String blockId = store.getBlockId(level, pos).orElse("minecraft:air");

        source.sendSuccess(() -> Component.literal(
                "Bloc EXTENDED en " + formatPos(pos) + " : " + blockId
                        + " | cube=" + CubePos.fromBlockPos(pos).storageKey()
        ), false);

        return 1;
    }

    private static int getCube(CommandContext<CommandSourceStack> ctx, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        CubePos cubePos = CubePos.fromBlockPos(pos);

        ctx.getSource().sendSuccess(() -> Component.literal(
                "Block " + formatPos(pos) + " appartient au cube "
                        + cubePos.x() + ", " + cubePos.y() + ", " + cubePos.z()
        ), false);

        return 1;
    }

    private static int stats(CommandContext<CommandSourceStack> ctx) {
        ExtendedBlockStore store = ExtendedBlockStore.get(ctx.getSource().getServer());

        ctx.getSource().sendSuccess(() -> Component.literal(
                "Descendre extended store : " + store.size() + " blocs stockés"
        ), false);

        return 1;
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }
}