package fr.descendre.storage;

import fr.descendre.core.DescendreConstants;
import fr.descendre.world.cube.CubePos;
import fr.descendre.world.cube.DescendreCube;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CubeSerializer {

    private CubeSerializer() {}

    public static CompoundTag serialize(DescendreCube cube) {
        CompoundTag tag = new CompoundTag();

        CubePos pos = cube.pos();
        tag.putInt("x", pos.x());
        tag.putInt("y", pos.y());
        tag.putInt("z", pos.z());

        List<BlockState> palette = new ArrayList<>();
        Map<BlockState, Integer> stateToIndex = new HashMap<>();

        BlockState air = Blocks.AIR.defaultBlockState();
        palette.add(air);
        stateToIndex.put(air, 0);

        int[] data = new int[DescendreConstants.CUBE_VOLUME];

        for (int ly = 0; ly < 16; ly++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    BlockState state = cube.getLocal(lx, ly, lz);
                    Integer idx = stateToIndex.get(state);
                    if (idx == null) {
                        idx = palette.size();
                        palette.add(state);
                        stateToIndex.put(state, idx);
                    }
                    data[index(lx, ly, lz)] = idx;
                }
            }
        }

        ListTag paletteTag = new ListTag();
        for (BlockState state : palette) {
            paletteTag.add(NbtUtils.writeBlockState(state));
        }
        tag.put("palette", paletteTag);
        tag.putIntArray("data", data);

        // Sérialise les BlockEntity
        net.minecraft.core.HolderLookup.Provider registries = net.minecraft.core.RegistryAccess.EMPTY;
        // Note : ici on n'a pas accès au level/registries du jeu, mais EMPTY suffit pour
        // la plupart des BE (ChestBE, FurnaceBE, etc. ne dépendent pas des registries dynamiques)
        net.minecraft.nbt.ListTag beList = cube.saveBlockEntities(registries);
        if (!beList.isEmpty()) {
            tag.put("blockEntities", beList);
        }

        return tag;
    }

    public static DescendreCube deserialize(CompoundTag tag) {
        int x = tag.getIntOr("x", 0);
        int y = tag.getIntOr("y", 0);
        int z = tag.getIntOr("z", 0);
        CubePos pos = new CubePos(x, y, z);

        DescendreCube cube = new DescendreCube(pos);

        ListTag paletteTag = tag.getListOrEmpty("palette");
        BlockState[] palette = new BlockState[paletteTag.size()];
        for (int i = 0; i < paletteTag.size(); i++) {
            CompoundTag entry = paletteTag.getCompoundOrEmpty(i);
            palette[i] = NbtUtils.readBlockState(BuiltInRegistries.BLOCK, entry);
        }

        int[] data = tag.getIntArray("data").orElse(new int[0]);
        if (data.length != DescendreConstants.CUBE_VOLUME) {
            return cube;
        }

        int worldOriginX = pos.x() << 4;
        int worldOriginY = pos.y() << 4;
        int worldOriginZ = pos.z() << 4;

        for (int ly = 0; ly < 16; ly++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    int paletteIdx = data[index(lx, ly, lz)];
                    if (paletteIdx >= 0 && paletteIdx < palette.length) {
                        BlockState state = palette[paletteIdx];
                        if (state != null && !state.isAir()) {
                            BlockPos worldPos = new BlockPos(
                                    worldOriginX + lx,
                                    worldOriginY + ly,
                                    worldOriginZ + lz
                            );
                            cube.setLocalDeserialize(lx, ly, lz, state, worldPos);
                        }
                    }
                }
            }
        }

        cube.markSaved();

        // Charge les BlockEntity (les BE ont déjà été créés via setLocalDeserialize)
        net.minecraft.nbt.ListTag beList = tag.getListOrEmpty("blockEntities");
        if (!beList.isEmpty()) {
            net.minecraft.core.HolderLookup.Provider registries = net.minecraft.core.RegistryAccess.EMPTY;
            cube.loadBlockEntities(beList, registries, null);
        }

        return cube;
    }

    private static int index(int lx, int ly, int lz) {
        return (ly << 8) | (lz << 4) | lx;
    }
}