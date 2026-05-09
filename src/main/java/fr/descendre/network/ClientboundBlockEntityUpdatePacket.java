package fr.descendre.network;

import fr.descendre.DescendreMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Payload serveur→client : "le BlockEntity à cette position a une nouvelle donnée NBT".
 * Coordonnées en int32 (pas tronquées contrairement à BlockPos.STREAM_CODEC).
 */
public record ClientboundBlockEntityUpdatePacket(
        int x, int y, int z,
        CompoundTag nbt
) implements CustomPacketPayload {

    public static final Type<ClientboundBlockEntityUpdatePacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(DescendreMod.MODID, "block_entity_update")
    );

    public static final StreamCodec<ByteBuf, ClientboundBlockEntityUpdatePacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ClientboundBlockEntityUpdatePacket decode(ByteBuf buf) {
                    int x = buf.readInt();
                    int y = buf.readInt();
                    int z = buf.readInt();
                    CompoundTag nbt = ByteBufCodecs.TRUSTED_COMPOUND_TAG.decode(buf);
                    return new ClientboundBlockEntityUpdatePacket(x, y, z, nbt);
                }

                @Override
                public void encode(ByteBuf buf, ClientboundBlockEntityUpdatePacket packet) {
                    buf.writeInt(packet.x);
                    buf.writeInt(packet.y);
                    buf.writeInt(packet.z);
                    ByteBufCodecs.TRUSTED_COMPOUND_TAG.encode(buf, packet.nbt);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static ClientboundBlockEntityUpdatePacket of(BlockPos pos, CompoundTag nbt) {
        return new ClientboundBlockEntityUpdatePacket(pos.getX(), pos.getY(), pos.getZ(), nbt);
    }

    public BlockPos pos() {
        return new BlockPos(x, y, z);
    }
}