package fr.descendre.network;

import fr.descendre.DescendreMod;
import fr.descendre.world.cube.CubePos;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Payload serveur→client : "oublie ce cube, libère sa RAM et son mesh".
 */
public record ClientboundForgetCubePacket(
        int cubeX, int cubeY, int cubeZ
) implements CustomPacketPayload {

    public static final Type<ClientboundForgetCubePacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(DescendreMod.MODID, "forget_cube")
    );

    public static final StreamCodec<ByteBuf, ClientboundForgetCubePacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ClientboundForgetCubePacket::cubeX,
                    ByteBufCodecs.VAR_INT, ClientboundForgetCubePacket::cubeY,
                    ByteBufCodecs.VAR_INT, ClientboundForgetCubePacket::cubeZ,
                    ClientboundForgetCubePacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static ClientboundForgetCubePacket of(CubePos pos) {
        return new ClientboundForgetCubePacket(pos.x(), pos.y(), pos.z());
    }

    public CubePos cubePos() {
        return new CubePos(cubeX, cubeY, cubeZ);
    }
}