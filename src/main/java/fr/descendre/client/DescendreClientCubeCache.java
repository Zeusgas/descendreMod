package fr.descendre.client;

import fr.descendre.core.DescendreConstants;
import fr.descendre.network.ClientboundCubeDataPacket;
import fr.descendre.world.cube.CubePos;
import fr.descendre.world.cube.DescendreCube;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache client des cubes Descendre.
 *
 * Singleton pour simplifier l'accès depuis les payloads. Comme tout le code client
 * tourne sur le thread principal Minecraft (via context.enqueueWork), pas besoin
 * de synchronisation lourde — un ConcurrentHashMap suffit pour la sécurité.
 */
public final class DescendreClientCubeCache {

    private static final DescendreClientCubeCache INSTANCE = new DescendreClientCubeCache();
    public static DescendreClientCubeCache getInstance() {
        return INSTANCE;
    }

    public static DescendreClientCubeCache get() {
        return INSTANCE;
    }

    private final Map<CubePos, DescendreCube> cubes = new ConcurrentHashMap<>();

    private DescendreClientCubeCache() {}

    /** Reçu par le handler de ClientboundCubeDataPacket. Reconstruit le cube et l'ajoute au cache. */
    public void putFromPacket(ClientboundCubeDataPacket packet) {
        CubePos pos = packet.cubePos();
        DescendreCube cube = new DescendreCube(pos);

        BlockState[] palette = packet.resolvePalette();
        int[] data = packet.data();

        for (int ly = 0; ly < 16; ly++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    int paletteIdx = data[(ly << 8) | (lz << 4) | lx];
                    if (paletteIdx >= 0 && paletteIdx < palette.length) {
                        BlockState state = palette[paletteIdx];
                        if (state != null && !state.isAir()) {
                            cube.setLocal(lx, ly, lz, state);
                        }
                    }
                }
            }
        }

        cubes.put(pos, cube);
        // Le cube vient d'être (ré)inséré : invalider son mesh
        fr.descendre.client.render.DescendreMeshCache.get().invalidate(pos);
    }

    /** Reçu par le handler de ClientboundCubeBlockUpdatePacket. */
    public void updateBlock(BlockPos pos, BlockState state) {
        CubePos cubePos = CubePos.fromBlockPos(pos);
        DescendreCube cube = cubes.get(cubePos);
        if (cube == null) {
            // Le cube n'est pas encore reçu : on ignore, le serveur enverra un CubeData complet plus tard.
            return;
        }
        cube.setLocal(
                CubePos.localX(pos.getX()),
                CubePos.localY(pos.getY()),
                CubePos.localZ(pos.getZ()),
                state
        );
        // Invalidation ciblée : ce cube + voisins sur les bords concernés
        fr.descendre.client.render.DescendreMeshCache.get().invalidateBlock(pos);
    }

    /** Reçu par le handler de ClientboundForgetCubePacket. */
    public void forget(CubePos pos) {
        cubes.remove(pos);
        fr.descendre.client.render.DescendreMeshCache.get().forget(pos);
        // Les voisins doivent recalculer leur culling : leurs faces vers ce cube sont peut-être à nouveau visibles
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
            CubePos neighbor = new CubePos(
                    pos.x() + dir.getStepX(),
                    pos.y() + dir.getStepY(),
                    pos.z() + dir.getStepZ()
            );
            fr.descendre.client.render.DescendreMeshCache.get().invalidate(neighbor);
        }
    }

    /** Lecture pour le rendu (J3+). Retourne AIR si le cube n'est pas connu. */
    public BlockState getBlock(BlockPos pos) {
        CubePos cubePos = CubePos.fromBlockPos(pos);
        DescendreCube cube = cubes.get(cubePos);
        if (cube == null) return Blocks.AIR.defaultBlockState();
        return cube.getLocal(
                CubePos.localX(pos.getX()),
                CubePos.localY(pos.getY()),
                CubePos.localZ(pos.getZ())
        );
    }

    public DescendreCube getCube(CubePos pos) {
        return cubes.get(pos);
    }

    public Collection<DescendreCube> allCubes() {
        return cubes.values();
    }

    public int size() {
        return cubes.size();
    }

    /** Vide le cache. À appeler à la déconnexion du serveur. */
    public void clear() {
        cubes.clear();
        fr.descendre.client.render.DescendreMeshCache.get().clear();
    }
}