package fr.descendre.world.cube;

import fr.descendre.core.DescendreConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.HolderLookup;
import java.util.HashMap;
import java.util.Map;




import java.util.Map;

public final class DescendreCube {
    private final CubePos pos;

    // null = air, pour éviter de remplir 4096 cases avec AIR
    private final BlockState[] states = new BlockState[DescendreConstants.CUBE_VOLUME];

    /** BlockEntities du cube, stockés par packed local position (lx + ly*16 + lz*256). */
    private final Map<Integer, BlockEntity> blockEntities = new HashMap<>();

    private int nonAirCount = 0;
    private boolean dirty = false;

    public DescendreCube(CubePos pos) {
        this.pos = pos;
    }

    public CubePos pos() {
        return pos;
    }

    public BlockState getLocal(int localX, int localY, int localZ) {
        checkLocal(localX, localY, localZ);

        BlockState state = states[index(localX, localY, localZ)];
        return state == null ? Blocks.AIR.defaultBlockState() : state;
    }

    /**
     * Pose un bloc et gère le cycle de vie du BlockEntity associé.
     * - Si l'ancien bloc avait un BE et que le nouveau bloc est différent → supprime l'ancien BE
     * - Si le nouveau bloc est un EntityBlock et différent de l'ancien → crée le BE
     *
     * Cette méthode doit être préférée à setLocal pour les setBlock initiés côté serveur,
     * pour que les BlockEntity soient correctement créés/détruits.
     */
    public void setLocalServer(int lx, int ly, int lz, BlockState state, ServerLevel level, BlockPos worldPos) {
        BlockState old = getLocal(lx, ly, lz);

        // Met à jour le bloc lui-même (réutilise setLocal qui existe déjà, qui notifie le changeListener)
        setLocal(lx, ly, lz, state);

        int key = lx | (ly << 4) | (lz << 8);

        boolean oldHadBE = old != null && old.getBlock() instanceof EntityBlock;
        boolean newHasBE = state != null && state.getBlock() instanceof EntityBlock;
        boolean blockTypeChanged = old == null || state == null || old.getBlock() != state.getBlock();

        if (oldHadBE && blockTypeChanged) {
            BlockEntity removed = blockEntities.remove(key);
            if (removed != null) {
                removed.setRemoved();
            }
        }

        if (state != null && pos.y() << 4 < -1000) {
            System.out.println("[CUBE-BE-SERVER] setLocalServer pos=" + worldPos + " state=" + state + " newHasBE=" + newHasBE + " blockTypeChanged=" + blockTypeChanged);
        }

        if (newHasBE && blockTypeChanged) {
            EntityBlock entityBlock = (EntityBlock) state.getBlock();
            BlockEntity be = entityBlock.newBlockEntity(worldPos, state);
            if (be != null) {
                be.setLevel(level);
                blockEntities.put(key, be);
            }
        }
    }

    /**
     * Récupère le BlockEntity à la position locale, ou null s'il n'y en a pas.
     */
    public BlockEntity getBlockEntityLocal(int lx, int ly, int lz) {
        return blockEntities.get(lx | (ly << 4) | (lz << 8));
    }

    /**
     * Map des BlockEntity (en lecture seule, pour itération).
     * La clé est packed (lx | ly << 4 | lz << 8).
     */
    public Map<Integer, BlockEntity> blockEntitiesView() {
        return java.util.Collections.unmodifiableMap(blockEntities);
    }





    public void setLocal(int localX, int localY, int localZ, BlockState newState) {
        checkLocal(localX, localY, localZ);

        int index = index(localX, localY, localZ);
        BlockState oldState = states[index];

        boolean oldAir = oldState == null || oldState.isAir();
        boolean newAir = newState == null || newState.isAir();

        if (oldAir && !newAir) {
            nonAirCount++;
        } else if (!oldAir && newAir) {
            nonAirCount--;
        }

        states[index] = newAir ? null : newState;
        dirty = true;
    }

    public boolean isEmpty() {
        return nonAirCount == 0;
    }

    public int nonAirCount() {
        return nonAirCount;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markSaved() {
        dirty = false;
    }

    private static int index(int localX, int localY, int localZ) {
        return (localY << 8) | (localZ << 4) | localX;
    }

    private static void checkLocal(int localX, int localY, int localZ) {
        if (localX < 0 || localX >= 16 || localY < 0 || localY >= 16 || localZ < 0 || localZ >= 16) {
            throw new IllegalArgumentException(
                    "Coordonnée locale invalide: " + localX + ", " + localY + ", " + localZ
            );
        }
    }

    /**
     * Sérialise tous les BlockEntity du cube vers une liste de NBT.
     * Format : pour chaque BE, on stocke {key: int (packed local), data: CompoundTag}.
     */

    /*

    public net.minecraft.nbt.ListTag saveBlockEntities(net.minecraft.core.HolderLookup.Provider registries) {

        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (Map.Entry<Integer, BlockEntity> entry : blockEntities.entrySet()) {
            net.minecraft.nbt.CompoundTag entryTag = new net.minecraft.nbt.CompoundTag();
            entryTag.putInt("key", entry.getKey());
            net.minecraft.nbt.CompoundTag beNbt = entry.getValue().saveWithFullMetadata(registries);
            entryTag.put("data", beNbt);
            list.add(entryTag);
        }
        return list;
    }

    /**
     * Restaure les BlockEntity depuis NBT (appelé après le chargement des blocs).
     * level peut être null en désérialisation pure (seront attachés au level plus tard).
     */
    /*public void loadBlockEntities(net.minecraft.nbt.ListTag list,
                                  net.minecraft.core.HolderLookup.Provider registries,
                                  net.minecraft.server.level.ServerLevel level) {
        blockEntities.clear();
        for (int i = 0; i < list.size(); i++) {
            net.minecraft.nbt.CompoundTag entryTag = list.getCompoundOrEmpty(i);
            int key = entryTag.getIntOr("key", -1);
            if (key < 0) continue;

            int lx = key & 0xF;
            int ly = (key >> 4) & 0xF;
            int lz = (key >> 8) & 0xF;
            BlockState state = getLocal(lx, ly, lz);
            if (state == null || !(state.getBlock() instanceof net.minecraft.world.level.block.EntityBlock entityBlock)) {
                continue;
            }

            net.minecraft.nbt.CompoundTag data = entryTag.getCompoundOrEmpty("data");

            // Reconstruit le worldPos
            net.minecraft.core.BlockPos worldPos = new net.minecraft.core.BlockPos(
                    (pos().x() << 4) + lx,
                    (pos().y() << 4) + ly,
                    (pos().z() << 4) + lz
            );

            BlockEntity be = entityBlock.newBlockEntity(worldPos, state);
            if (be == null) continue;

            be.loadWithComponents(data, registries);
            if (level != null) be.setLevel(level);

            blockEntities.put(key, be);
        }
    }*/

    /** Sérialisation NBT — désactivée temporairement (TODO étape future). */
    public net.minecraft.nbt.ListTag saveBlockEntities(net.minecraft.core.HolderLookup.Provider registries) {
        return new net.minecraft.nbt.ListTag();
    }

    /** Désérialisation NBT — désactivée temporairement (TODO étape future). */
    public void loadBlockEntities(net.minecraft.nbt.ListTag list,
                                  net.minecraft.core.HolderLookup.Provider registries,
                                  net.minecraft.server.level.ServerLevel level) {
        // no-op
    }

}