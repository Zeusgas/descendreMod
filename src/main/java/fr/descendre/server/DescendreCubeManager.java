package fr.descendre.server;

import fr.descendre.world.cube.CubeMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DescendreCubeManager {
    private static final Map<ResourceKey<Level>, CubeMap> LEVEL_CUBES = new ConcurrentHashMap<>();

    private DescendreCubeManager() {}

    public static CubeMap get(ServerLevel level) {
        return LEVEL_CUBES.computeIfAbsent(level.dimension(), ignored -> new CubeMap());
    }

    public static void clear(ServerLevel level) {
        LEVEL_CUBES.remove(level.dimension());
    }
}