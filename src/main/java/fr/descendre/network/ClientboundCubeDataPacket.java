package fr.descendre.network;

import fr.descendre.DescendreMod;
import fr.descendre.core.DescendreConstants;
import fr.descendre.world.cube.CubePos;
import fr.descendre.world.cube.DescendreCube;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Payload serveur→client : "voici un cube complet, mémorise-le".
 *
 * Format réseau (palette compressée) :
 *   - x, y, z (3 × int)              : position du cube
 *   - paletteSize (varInt)            : nombre d'entrées palette
 *   - palette[paletteSize] (varInt)   : ID de chaque BlockState (depuis Block.BLOCK_STATE_REGISTRY)
 *   - data[4096] (varInt)             : indices vers la palette
 */
public record ClientboundCubeDataPacket(
        int cubeX, int cubeY, int cubeZ,
        int[] paletteIds,
        int[] data
) implements CustomPacketPayload {

    public static final Type<ClientboundCubeDataPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(DescendreMod.MODID, "cube_data")
    );

    public static final StreamCodec<ByteBuf, ClientboundCubeDataPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ClientboundCubeDataPacket decode(ByteBuf buf) {
                    int x = buf.readInt();
                    int y = buf.readInt();
                    int z = buf.readInt();

                    int paletteSize = ByteBufCodecs.VAR_INT.decode(buf);
                    int[] palette = new int[paletteSize];
                    for (int i = 0; i < paletteSize; i++) {
                        palette[i] = ByteBufCodecs.VAR_INT.decode(buf);
                    }

                    int[] data = new int[DescendreConstants.CUBE_VOLUME];
                    for (int i = 0; i < data.length; i++) {
                        data[i] = ByteBufCodecs.VAR_INT.decode(buf);
                    }
                    return new ClientboundCubeDataPacket(x, y, z, palette, data);
                }

                @Override
                public void encode(ByteBuf buf, ClientboundCubeDataPacket packet) {
                    buf.writeInt(packet.cubeX);
                    buf.writeInt(packet.cubeY);
                    buf.writeInt(packet.cubeZ);

                    ByteBufCodecs.VAR_INT.encode(buf, packet.paletteIds.length);
                    for (int id : packet.paletteIds) {
                        ByteBufCodecs.VAR_INT.encode(buf, id);
                    }
                    for (int idx : packet.data) {
                        ByteBufCodecs.VAR_INT.encode(buf, idx);
                    }
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Construit un payload depuis un DescendreCube (côté serveur). */
    public static ClientboundCubeDataPacket fromCube(DescendreCube cube) {
        List<BlockState> palette = new ArrayList<>();
        Map<BlockState, Integer> stateToIndex = new HashMap<>();

        // Index 0 = air par convention
        BlockState air = Blocks.AIR.defaultBlockState();
        palette.add(air);
        stateToIndex.put(air, 0);

        int[] data = new int[DescendreConstants.CUBE_VOLUME];
        for (int ly = 0; ly < 16; ly++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    BlockState state = cube.getLocal(lx, ly, lz);
                    if (state == null) state = air;
                    Integer idx = stateToIndex.get(state);
                    if (idx == null) {
                        idx = palette.size();
                        palette.add(state);
                        stateToIndex.put(state, idx);
                    }
                    data[(ly << 8) | (lz << 4) | lx] = idx;
                }
            }
        }

        int[] paletteIds = new int[palette.size()];
        for (int i = 0; i < palette.size(); i++) {
            paletteIds[i] = Block.BLOCK_STATE_REGISTRY.getId(palette.get(i));
        }

        CubePos pos = cube.pos();
        return new ClientboundCubeDataPacket(pos.x(), pos.y(), pos.z(), paletteIds, data);
    }

    /** Reconstruit la palette en BlockState (côté client). */
    public BlockState[] resolvePalette() {
        BlockState[] result = new BlockState[paletteIds.length];
        for (int i = 0; i < paletteIds.length; i++) {
            BlockState state = Block.BLOCK_STATE_REGISTRY.byId(paletteIds[i]);
            result[i] = state != null ? state : Blocks.AIR.defaultBlockState();
        }
        return result;
    }

    public CubePos cubePos() {
        return new CubePos(cubeX, cubeY, cubeZ);
    }
}