package fr.descendre.network;

import fr.descendre.DescendreMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Payload serveur→client : block event (animation coffre, note block...) avec
 * coords int32 au lieu de BlockPos tronqué.
 */
public record ClientboundCubicBlockEventPacket(
        int x, int y, int z,
        int blockId,
        int paramA,  // event type (1 = ouverture coffre)
        int paramB   // event param (compteur d'ouvreurs)
) implements CustomPacketPayload {

    public static final Type<ClientboundCubicBlockEventPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(DescendreMod.MODID, "cubic_block_event")
    );

    public static final StreamCodec<ByteBuf, ClientboundCubicBlockEventPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ClientboundCubicBlockEventPacket decode(ByteBuf buf) {
                    int x = buf.readInt();
                    int y = buf.readInt();
                    int z = buf.readInt();
                    int blockId = ByteBufCodecs.VAR_INT.decode(buf);
                    int paramA = ByteBufCodecs.VAR_INT.decode(buf);
                    int paramB = ByteBufCodecs.VAR_INT.decode(buf);
                    return new ClientboundCubicBlockEventPacket(x, y, z, blockId, paramA, paramB);
                }

                @Override
                public void encode(ByteBuf buf, ClientboundCubicBlockEventPacket packet) {
                    buf.writeInt(packet.x);
                    buf.writeInt(packet.y);
                    buf.writeInt(packet.z);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.blockId);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.paramA);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.paramB);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static ClientboundCubicBlockEventPacket of(int x, int y, int z, int blockId, int paramA, int paramB) {
        return new ClientboundCubicBlockEventPacket(x, y, z, blockId, paramA, paramB);
    }

    public net.minecraft.core.BlockPos pos() {
        return new net.minecraft.core.BlockPos(x, y, z);
    }
}