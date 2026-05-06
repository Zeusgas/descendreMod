package fr.descendre.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.descendre.core.DescendreHeight;
import fr.descendre.world.cube.CubeMap;
import fr.descendre.world.cube.CubePos;
import fr.descendre.world.cube.DescendreCube;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DescendreCubeDiskStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private DescendreCubeDiskStorage() {}

    public static int save(ServerLevel level, CubeMap map) throws IOException {
        Path file = fileFor(level);
        Files.createDirectories(file.getParent());

        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.addProperty("dimension", level.dimension().identifier().toString());

        JsonArray blocks = new JsonArray();

        for (DescendreCube cube : map.allCubes()) {
            CubePos cubePos = cube.pos();

            int originX = cubePos.x() << 4;
            int originY = cubePos.y() << 4;
            int originZ = cubePos.z() << 4;

            for (int ly = 0; ly < 16; ly++) {
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        BlockState state = cube.getLocal(lx, ly, lz);

                        if (state == null || state.isAir()) {
                            continue;
                        }

                        JsonObject entry = new JsonObject();
                        entry.addProperty("x", originX + lx);
                        entry.addProperty("y", originY + ly);
                        entry.addProperty("z", originZ + lz);
                        entry.addProperty("id", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());

                        blocks.add(entry);
                    }
                }
            }
        }

        root.add("blocks", blocks);

        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        }

        return blocks.size();
    }

    public static int load(ServerLevel level, CubeMap map) throws IOException {
        Path file = fileFor(level);

        if (!Files.exists(file)) {
            return 0;
        }

        JsonObject root;

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        }

        JsonArray blocks = root.getAsJsonArray("blocks");

        if (blocks == null) {
            return 0;
        }

        int loaded = 0;

        for (JsonElement element : blocks) {
            JsonObject entry = element.getAsJsonObject();

            int x = entry.get("x").getAsInt();
            int y = entry.get("y").getAsInt();
            int z = entry.get("z").getAsInt();

            if (!DescendreHeight.isInsideInternalRange(y)) {
                continue;
            }

            String id = entry.get("id").getAsString();
            Identifier location = Identifier.parse(id);

            Block block = BuiltInRegistries.BLOCK.getValue(location);

            if (block == null || block == Blocks.AIR) {
                continue;
            }

            map.setBlock(new BlockPos(x, y, z), block.defaultBlockState());
            loaded++;
        }

        return loaded;
    }

    private static Path fileFor(ServerLevel level) {
        MinecraftServer server = level.getServer();

        String dimensionName = level.dimension().identifier().toString()
                .replace(':', '_')
                .replace('/', '_');

        return server.getWorldPath(LevelResource.ROOT)
                .resolve("descendre")
                .resolve("cubes")
                .resolve(dimensionName + ".json");
    }
}