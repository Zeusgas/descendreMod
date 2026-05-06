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
 * Beaucoup plus léger qu'un cube complet : ~12 octets au lieu de ~16 ko.
 */
public record ClientboundCubeBlockUpdatePacket(
        BlockPos pos,
        int blockStateId
) implements CustomPacketPayload {

    public static final Type<ClientboundCubeBlockUpdatePacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(DescendreMod.MODID, "cube_block_update")
    );

    public static final StreamCodec<ByteBuf, ClientboundCubeBlockUpdatePacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC,         ClientboundCubeBlockUpdatePacket::pos,
                    ByteBufCodecs.VAR_INT,         ClientboundCubeBlockUpdatePacket::blockStateId,
                    ClientboundCubeBlockUpdatePacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static ClientboundCubeBlockUpdatePacket of(BlockPos pos, BlockState state) {
        return new ClientboundCubeBlockUpdatePacket(pos.immutable(), Block.BLOCK_STATE_REGISTRY.getId(state));
    }

    public BlockState resolveState() {
        BlockState state = Block.BLOCK_STATE_REGISTRY.byId(blockStateId);
        return state != null ? state : Blocks.AIR.defaultBlockState();
    }
}