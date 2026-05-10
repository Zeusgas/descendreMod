package fr.descendre.network;

import fr.descendre.DescendreMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Payload serveur→client : équivalent de ClientboundLevelEventPacket mais avec
 * coords int32 (pas tronquées comme BlockPos.STREAM_CODEC).
 *
 * Utilisé pour les événements de niveau (particules de cassage 2001, etc.)
 * qui se produisent à des Y hors range vanilla.
 */
public record ClientboundLevelEventCubicPacket(
        int eventId,
        int x, int y, int z,
        int data,
        boolean globalEvent
) implements CustomPacketPayload {

    public static final Type<ClientboundLevelEventCubicPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(DescendreMod.MODID, "level_event_cubic")
    );

    public static final StreamCodec<ByteBuf, ClientboundLevelEventCubicPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ClientboundLevelEventCubicPacket decode(ByteBuf buf) {
                    int eventId = ByteBufCodecs.VAR_INT.decode(buf);
                    int x = buf.readInt();
                    int y = buf.readInt();
                    int z = buf.readInt();
                    int data = ByteBufCodecs.VAR_INT.decode(buf);
                    boolean globalEvent = buf.readBoolean();
                    return new ClientboundLevelEventCubicPacket(eventId, x, y, z, data, globalEvent);
                }

                @Override
                public void encode(ByteBuf buf, ClientboundLevelEventCubicPacket packet) {
                    ByteBufCodecs.VAR_INT.encode(buf, packet.eventId);
                    buf.writeInt(packet.x);
                    buf.writeInt(packet.y);
                    buf.writeInt(packet.z);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.data);
                    buf.writeBoolean(packet.globalEvent);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public BlockPos pos() {
        return new BlockPos(x, y, z);
    }

    public static ClientboundLevelEventCubicPacket of(int eventId, BlockPos pos, int data, boolean globalEvent) {
        return new ClientboundLevelEventCubicPacket(eventId, pos.getX(), pos.getY(), pos.getZ(), data, globalEvent);
    }
}