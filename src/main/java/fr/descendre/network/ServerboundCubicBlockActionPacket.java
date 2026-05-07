package fr.descendre.network;

import fr.descendre.DescendreMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Payload client→serveur : "je veux casser/poser un bloc cubic à cette position".
 *
 * On utilise des ints 32 bits pour x/y/z (pas BlockPos.STREAM_CODEC qui tronque Y à 12 bits).
 *
 * Le serveur valide (range, portée) avant d'appliquer.
 */
public record ServerboundCubicBlockActionPacket(
        int x, int y, int z,
        int blockStateId,
        boolean isPlace
) implements CustomPacketPayload {

    public static final Type<ServerboundCubicBlockActionPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(DescendreMod.MODID, "cubic_block_action")
    );

    public static final StreamCodec<ByteBuf, ServerboundCubicBlockActionPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ServerboundCubicBlockActionPacket decode(ByteBuf buf) {
                    int x = buf.readInt();
                    int y = buf.readInt();
                    int z = buf.readInt();
                    int stateId = ByteBufCodecs.VAR_INT.decode(buf);
                    boolean isPlace = buf.readBoolean();
                    return new ServerboundCubicBlockActionPacket(x, y, z, stateId, isPlace);
                }

                @Override
                public void encode(ByteBuf buf, ServerboundCubicBlockActionPacket packet) {
                    buf.writeInt(packet.x);
                    buf.writeInt(packet.y);
                    buf.writeInt(packet.z);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.blockStateId);
                    buf.writeBoolean(packet.isPlace);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static ServerboundCubicBlockActionPacket breakAt(BlockPos pos) {
        return new ServerboundCubicBlockActionPacket(
                pos.getX(), pos.getY(), pos.getZ(),
                Block.BLOCK_STATE_REGISTRY.getId(Blocks.AIR.defaultBlockState()),
                false
        );
    }

    public static ServerboundCubicBlockActionPacket placeAt(BlockPos pos, BlockState state) {
        return new ServerboundCubicBlockActionPacket(
                pos.getX(), pos.getY(), pos.getZ(),
                Block.BLOCK_STATE_REGISTRY.getId(state),
                true
        );
    }

    public BlockPos pos() {
        return new BlockPos(x, y, z);
    }

    public BlockState resolveState() {
        BlockState state = Block.BLOCK_STATE_REGISTRY.byId(blockStateId);
        return state != null ? state : Blocks.AIR.defaultBlockState();
    }
}