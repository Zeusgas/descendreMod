package fr.descendre.collision;

import fr.descendre.client.DescendreClientCubeCache;
import fr.descendre.core.DescendreHeight;
import fr.descendre.server.DescendreCubeManager;
import fr.descendre.world.cube.CubeMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Retourne les collisions Descendre côté serveur ET côté client.
 *
 * - Côté serveur : utilise le CubeMap du DescendreCubeManager (vérité absolue).
 * - Côté client  : utilise le DescendreClientCubeCache (snapshot envoyé par le serveur).
 *
 * Si les deux sont désynchronisés (cube pas encore reçu côté client par exemple),
 * le serveur fera autorité et corrigera la position du joueur après quelques ticks.
 */
public final class DescendreCollisionProvider {

    private DescendreCollisionProvider() {}

    public static List<VoxelShape> getCollisionShapes(Level level, Entity entity, AABB collisionBox) {
        BiFunction<Level, BlockPos, BlockState> blockLookup = blockLookupFor(level);
        if (blockLookup == null) return Collections.emptyList();

        AABB box = collisionBox.inflate(1.0E-7D);

        int minX = Mth.floor(box.minX);
        int maxX = Mth.floor(box.maxX);
        int minY = Mth.floor(box.minY);
        int maxY = Mth.floor(box.maxY);
        int minZ = Mth.floor(box.minZ);
        int maxZ = Mth.floor(box.maxZ);

        List<VoxelShape> shapes = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int y = minY; y <= maxY; y++) {
            if (!DescendreHeight.isInsideInternalRange(y)) continue;

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    pos.set(x, y, z);

                    BlockState state = blockLookup.apply(level, pos);
                    if (state == null || state.isAir()) continue;

                    VoxelShape shape = Shapes.create(
                            new AABB(x, y, z, x + 1.0D, y + 1.0D, z + 1.0D)
                    );
                    shapes.add(shape);
                }
            }
        }

        return shapes;
    }

    /**
     * Retourne le bon "lookup" selon le côté (serveur vs client),
     * ou null si on n'a pas de stockage Descendre actif sur ce niveau.
     */
    private static BiFunction<Level, BlockPos, BlockState> blockLookupFor(Level level) {
        if (level instanceof ServerLevel serverLevel) {
            CubeMap map = DescendreCubeManager.get(serverLevel);
            return (l, p) -> map.getBlock(p);
        }
        if (level instanceof ClientLevel) {
            DescendreClientCubeCache cache = DescendreClientCubeCache.get();
            return (l, p) -> cache.getBlock(p);
        }
        return null;
    }

    /**
     * Lookup unique d'un bloc cubic, utilisé par le mixin getBlockState.
     * Retourne null si pas de stockage Descendre actif sur ce niveau ou si le bloc est air.
     */
    public static BlockState lookupBlock(Level level, BlockPos pos) {
        if (!DescendreHeight.isInsideInternalRange(pos.getY())) return null;

        BiFunction<Level, BlockPos, BlockState> lookup = blockLookupFor(level);
        if (lookup == null) return null;

        BlockState state = lookup.apply(level, pos);
        return state == null || state.isAir() ? null : state;
    }

}