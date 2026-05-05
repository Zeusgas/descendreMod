package fr.descendre.storage;

import fr.descendre.core.DescendreConstants;
import fr.descendre.world.cube.CubePos;
import fr.descendre.world.cube.DescendreCube;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sérialise et désérialise un DescendreCube vers/depuis NBT.
 *
 * Format NBT :
 * {
 *   "x": int, "y": int, "z": int,           // position du cube
 *   "palette": [BlockState, ...],            // liste unique des BlockStates utilisés
 *   "data": int[]                            // 4096 indices vers la palette (un par bloc)
 * }
 *
 * On utilise une palette pour économiser : un cube de pierre pleine prend
 * ~30 octets au lieu de 32 ko (avant gzip).
 */
public final class CubeSerializer {

    private CubeSerializer() {}

    public static CompoundTag serialize(DescendreCube cube) {
        CompoundTag tag = new CompoundTag();

        CubePos pos = cube.pos();
        tag.putInt("x", pos.x());
        tag.putInt("y", pos.y());
        tag.putInt("z", pos.z());

        // Construction de la palette
        List<BlockState> palette = new ArrayList<>();
        Map<BlockState, Integer> stateToIndex = new HashMap<>();

        // Index 0 = AIR par convention
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

        // Sérialisation de la palette
        ListTag paletteTag = new ListTag();
        for (BlockState state : palette) {
            paletteTag.add(NbtUtils.writeBlockState(state));
        }
        tag.put("palette", paletteTag);
        tag.putIntArray("data", data);

        return tag;
    }

    public static DescendreCube deserialize(CompoundTag tag) {
        int x = tag.getIntOr("x", 0);
        int y = tag.getIntOr("y", 0);
        int z = tag.getIntOr("z", 0);
        CubePos pos = new CubePos(x, y, z);

        DescendreCube cube = new DescendreCube(pos);

        // Reconstruction de la palette
        ListTag paletteTag = tag.getListOrEmpty("palette");
        BlockState[] palette = new BlockState[paletteTag.size()];
        for (int i = 0; i < paletteTag.size(); i++) {
            CompoundTag entry = paletteTag.getCompoundOrEmpty(i);
            palette[i] = NbtUtils.readBlockState(BuiltInRegistries.BLOCK, entry);
        }

        int[] data = tag.getIntArray("data").orElse(new int[0]);
        if (data.length != DescendreConstants.CUBE_VOLUME) {
            // Données corrompues ou format ancien : on retourne un cube vide
            return cube;
        }

        for (int ly = 0; ly < 16; ly++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    int paletteIdx = data[index(lx, ly, lz)];
                    if (paletteIdx >= 0 && paletteIdx < palette.length) {
                        BlockState state = palette[paletteIdx];
                        if (state != null && !state.isAir()) {
                            cube.setLocal(lx, ly, lz, state);
                        }
                    }
                }
            }
        }

        cube.markSaved();
        return cube;
    }

    private static int index(int lx, int ly, int lz) {
        return (ly << 8) | (lz << 4) | lx;
    }
}