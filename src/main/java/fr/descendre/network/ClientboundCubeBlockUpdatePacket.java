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
 * Payload serveur→client : "à cette position, le bloc a changé".
 *
 * IMPORTANT : on encode x, y, z en 3 ints séparés au lieu d'utiliser BlockPos.STREAM_CODEC.
 * Le codec vanilla pack la position en long avec seulement 12 bits pour Y, ce qui tronque
 * tout Y hors de [-2048, 2047]. Pour Descendre où Y peut aller jusqu'à -15000, on doit
 * utiliser des ints 32 bits qui couvrent toute la range.
 */
public record ClientboundCubeBlockUpdatePacket(
        int x, int y, int z,
        int blockStateId
) implements CustomPacketPayload {

    public static final Type<ClientboundCubeBlockUpdatePacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(DescendreMod.MODID, "cube_block_update")
    );

    public static final StreamCodec<ByteBuf, ClientboundCubeBlockUpdatePacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ClientboundCubeBlockUpdatePacket decode(ByteBuf buf) {
                    int x = buf.readInt();
                    int y = buf.readInt();
                    int z = buf.readInt();
                    int stateId = ByteBufCodecs.VAR_INT.decode(buf);
                    return new ClientboundCubeBlockUpdatePacket(x, y, z, stateId);
                }

                @Override
                public void encode(ByteBuf buf, ClientboundCubeBlockUpdatePacket packet) {
                    buf.writeInt(packet.x);
                    buf.writeInt(packet.y);
                    buf.writeInt(packet.z);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.blockStateId);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static ClientboundCubeBlockUpdatePacket of(BlockPos pos, BlockState state) {
        return new ClientboundCubeBlockUpdatePacket(
                pos.getX(), pos.getY(), pos.getZ(),
                Block.BLOCK_STATE_REGISTRY.getId(state)
        );
    }

    /** Reconstruit un BlockPos à partir des coords. */
    public BlockPos pos() {
        return new BlockPos(x, y, z);
    }

    public BlockState resolveState() {
        BlockState state = Block.BLOCK_STATE_REGISTRY.byId(blockStateId);
        return state != null ? state : Blocks.AIR.defaultBlockState();
    }
}