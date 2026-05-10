package fr.descendre.client;

import fr.descendre.core.DescendreConstants;
import fr.descendre.network.ClientboundCubeDataPacket;
import fr.descendre.world.cube.CubePos;
import fr.descendre.world.cube.DescendreCube;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
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



    /** Tick tous les BlockEntity cubic. À appeler chaque ClientTickEvent.Post. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void tickBlockEntities() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) return;

        for (java.util.Map.Entry<net.minecraft.core.BlockPos, net.minecraft.world.level.block.entity.BlockEntity> entry
                : blockEntities.entrySet()) {
            net.minecraft.core.BlockPos pos = entry.getKey();
            net.minecraft.world.level.block.entity.BlockEntity be = entry.getValue();
            if (be == null || be.isRemoved()) continue;

            net.minecraft.world.level.block.state.BlockState state = getBlock(pos);
            if (state == null) continue;

            net.minecraft.world.level.block.entity.BlockEntityTicker ticker =
                    state.getTicker(mc.level, be.getType());
            if (ticker == null) continue;

            try {
                ticker.tick(mc.level, pos, state, be);
            } catch (Exception e) {
                System.err.println("[Descendre] Erreur tick BE @ " + pos + ": " + e.getMessage());
            }
        }
    }



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
        // Crée les BlockEntity côté client à partir des positions reçues
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level != null) {
            int worldOriginX = pos.x() << 4;
            int worldOriginY = pos.y() << 4;
            int worldOriginZ = pos.z() << 4;
            for (int packed : packet.beLocalKeys()) {
                int lx = packed & 0xF;
                int ly = (packed >> 4) & 0xF;
                int lz = (packed >> 8) & 0xF;
                BlockState state = cube.getLocal(lx, ly, lz);
                if (state == null || !(state.getBlock() instanceof net.minecraft.world.level.block.EntityBlock entityBlock)) continue;

                net.minecraft.core.BlockPos worldPos = new net.minecraft.core.BlockPos(
                        worldOriginX + lx, worldOriginY + ly, worldOriginZ + lz
                );
                net.minecraft.world.level.block.entity.BlockEntity be = entityBlock.newBlockEntity(worldPos, state);
                if (be == null) continue;
                be.setLevel(mc.level);
                blockEntities.put(worldPos, be);
            }
        }

        if (pos.y() < -100 && packet.beLocalKeys().length > 0) {
            System.out.println("[CUBE-BE-CLIENT] cubePos=" + pos + " received " + packet.beLocalKeys().length + " BE keys");
            for (int k : packet.beLocalKeys()) {
                System.out.println("  key=" + k + " (lx=" + (k & 0xF) + " ly=" + ((k >> 4) & 0xF) + " lz=" + ((k >> 8) & 0xF) + ")");
            }
            System.out.println("[CUBE-BE-CLIENT] total BE in cache after: " + blockEntities.size());
        }

        cubes.put(pos, cube);
        // Le cube vient d'être (ré)inséré : invalider son mesh
        fr.descendre.client.render.DescendreMeshCache.get().invalidate(pos);
    }

    /** Reçu par le handler de ClientboundCubeBlockUpdatePacket. */
    public void updateBlock(BlockPos pos, BlockState state) {

        if (pos.getY() < -1000) {
            System.out.println("[CLIENT-UPDATE-BLOCK] pos=" + pos + " state=" + state);
        }

        CubePos cubePos = CubePos.fromBlockPos(pos);
        DescendreCube cube = cubes.get(cubePos);
        if (cube == null) {
            return;
        }

        BlockState old = cube.getLocal(
                CubePos.localX(pos.getX()),
                CubePos.localY(pos.getY()),
                CubePos.localZ(pos.getZ())
        );

        cube.setLocal(
                CubePos.localX(pos.getX()),
                CubePos.localY(pos.getY()),
                CubePos.localZ(pos.getZ()),
                state
        );

        // Gestion du BlockEntity côté client (symétrique à setLocalServer)
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level != null) {
            BlockPos immutable = pos.immutable();
            boolean oldHadBE = old != null && old.getBlock() instanceof net.minecraft.world.level.block.EntityBlock;
            boolean newHasBE = state != null && state.getBlock() instanceof net.minecraft.world.level.block.EntityBlock;
            boolean blockTypeChanged = old == null || state == null || old.getBlock() != state.getBlock();

            if (oldHadBE && blockTypeChanged) {
                net.minecraft.world.level.block.entity.BlockEntity removed = blockEntities.remove(immutable);
                if (removed != null) removed.setRemoved();
            }

            if (newHasBE && blockTypeChanged) {
                net.minecraft.world.level.block.EntityBlock entityBlock =
                        (net.minecraft.world.level.block.EntityBlock) state.getBlock();
                net.minecraft.world.level.block.entity.BlockEntity be = entityBlock.newBlockEntity(immutable, state);
                if (be != null) {
                    be.setLevel(mc.level);
                    blockEntities.put(immutable, be);
                    System.out.println("[CUBE-BE-CLIENT-UPDATE] created BE at " + immutable + " type=" + be.getType());
                }
            }

            else if (newHasBE && !blockTypeChanged) {
                // Même type de bloc, mais state changé (ex: coffre SINGLE → RIGHT)
                // → mettre à jour le state du BE existant pour que le rendu suive
                net.minecraft.world.level.block.entity.BlockEntity existing = blockEntities.get(immutable);
                if (existing != null) {
                    existing.setBlockState(state);
                    System.out.println("[BE-STATE-UPDATE] pos=" + immutable + " newState=" + state);
                }
             }

        }

        fr.descendre.client.render.DescendreMeshCache.get().invalidateBlock(pos);
    }

    /** Reçu par le handler de ClientboundForgetCubePacket. */
    public void forget(CubePos pos) {
        DescendreCube cube = cubes.remove(pos);
        // Supprime les BlockEntity associés
        if (cube != null) {
            int worldOriginX = pos.x() << 4;
            int worldOriginY = pos.y() << 4;
            int worldOriginZ = pos.z() << 4;
            for (int ly = 0; ly < 16; ly++) {
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        net.minecraft.core.BlockPos wp = new net.minecraft.core.BlockPos(
                                worldOriginX + lx, worldOriginY + ly, worldOriginZ + lz
                        );
                        blockEntities.remove(wp);
                    }
                }
            }
        }
        fr.descendre.client.render.DescendreMeshCache.get().forget(pos);
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

    /** Map des BlockEntity côté client, par BlockPos. */
    private final java.util.Map<net.minecraft.core.BlockPos, net.minecraft.world.level.block.entity.BlockEntity> blockEntities
            = new java.util.concurrent.ConcurrentHashMap<>();

    /** Récupère un BlockEntity côté client. */
    public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(net.minecraft.core.BlockPos pos) {
        return blockEntities.get(pos);
    }

    /** Met à jour ou crée un BlockEntity côté client à partir d'un NBT. */
    public void updateBlockEntity(net.minecraft.core.BlockPos pos, net.minecraft.nbt.CompoundTag nbt) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) return;

        net.minecraft.world.level.block.state.BlockState state = getBlock(pos);
        if (state == null || !(state.getBlock() instanceof net.minecraft.world.level.block.EntityBlock entityBlock)) {
            blockEntities.remove(pos);
            return;
        }

        net.minecraft.world.level.block.entity.BlockEntity be = blockEntities.get(pos);
        if (be == null || be.getType() != entityBlock.newBlockEntity(pos, state).getType()) {
            be = entityBlock.newBlockEntity(pos, state);
            if (be == null) return;
            be.setLevel(mc.level);
            blockEntities.put(pos.immutable(), be);
        }

        // TODO : appliquer le NBT via loadWithComponents (API ValueInput, à voir étape future)
    }

    /** Supprime un BlockEntity côté client (quand on oublie un cube par exemple). */
    public void removeBlockEntity(net.minecraft.core.BlockPos pos) {
        blockEntities.remove(pos);
    }

    /** Retourne tous les BlockEntity cubic actuellement en cache. */
    public java.util.Set<net.minecraft.world.level.block.entity.BlockEntity> getAllBlockEntities() {
        return new java.util.HashSet<>(blockEntities.values());
    }

}