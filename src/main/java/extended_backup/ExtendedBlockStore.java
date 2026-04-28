package extended_backup;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

public final class ExtendedBlockStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<MinecraftServer, ExtendedBlockStore> STORES = new WeakHashMap<>();

    private final Path path;
    private final Map<String, String> blocks = new HashMap<>();

    private boolean dirty = false;

    private ExtendedBlockStore(MinecraftServer server) {
        this.path = server.getWorldPath(LevelResource.ROOT)
                .resolve("data")
                .resolve("descendre_extended_blocks.json");

        load();
    }

    public static synchronized ExtendedBlockStore get(MinecraftServer server) {
        return STORES.computeIfAbsent(server, ExtendedBlockStore::new);
    }

    public static synchronized void saveAndUnload(MinecraftServer server) {
        ExtendedBlockStore store = STORES.remove(server);
        if (store != null) {
            store.saveNow();
        }
    }

    public void set(ServerLevel level, BlockPos pos, String blockId) {
        blocks.put(makeKey(level, pos), blockId);
        dirty = true;
    }

    public boolean remove(ServerLevel level, BlockPos pos) {
        String removed = blocks.remove(makeKey(level, pos));
        if (removed != null) {
            dirty = true;
            return true;
        }
        return false;
    }

    public Optional<String> getBlockId(ServerLevel level, BlockPos pos) {
        return Optional.ofNullable(blocks.get(makeKey(level, pos)));
    }

    public int size() {
        return blocks.size();
    }

    public void saveIfDirty() {
        if (dirty) {
            saveNow();
        }
    }

    public void saveNow() {
        try {
            Files.createDirectories(path.getParent());

            FileFormat format = new FileFormat();
            format.blocks.putAll(this.blocks);

            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(format, writer);
            }

            dirty = false;
        } catch (Exception e) {
            throw new RuntimeException("Impossible de sauvegarder les blocs extended Descendre", e);
        }
    }

    private void load() {
        if (!Files.exists(path)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            FileFormat format = GSON.fromJson(reader, FileFormat.class);

            if (format != null && format.blocks != null) {
                blocks.clear();
                blocks.putAll(format.blocks);
            }

            dirty = false;
        } catch (Exception e) {
            throw new RuntimeException("Impossible de charger les blocs extended Descendre", e);
        }
    }

    private static String makeKey(ServerLevel level, BlockPos pos) {
        String dimensionId = cleanDimensionId(level.dimension().toString());

        return dimensionId + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static String cleanDimensionId(String raw) {
        int slash = raw.lastIndexOf('/');
        int end = raw.lastIndexOf(']');

        if (slash >= 0 && end > slash) {
            return raw.substring(slash + 1, end).trim();
        }

        return raw;
    }

    private static final class FileFormat {
        Map<String, String> blocks = new HashMap<>();
    }
}