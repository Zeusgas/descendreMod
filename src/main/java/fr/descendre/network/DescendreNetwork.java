package fr.descendre.network;

import fr.descendre.DescendreMod;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import fr.descendre.client.DescendreClientCubeCache;

/**
 * Enregistre les payloads Descendre auprès de NeoForge et expose des helpers d'envoi.
 *
 * Côté serveur : sendToPlayer / broadcast
 * Côté client  : les handlers reçoivent les packets et les transmettent au cache client.
 */
public final class DescendreNetwork {

    private static final String VERSION = "1";

    private DescendreNetwork() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(DescendreNetwork::onRegisterPayloads);
    }

    @SubscribeEvent
    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(DescendreMod.MODID).versioned(VERSION);

        registrar.playToClient(
                ClientboundCubeDataPacket.TYPE,
                ClientboundCubeDataPacket.STREAM_CODEC,
                DescendreNetwork::handleCubeData
        );

        registrar.playToClient(
                ClientboundCubeBlockUpdatePacket.TYPE,
                ClientboundCubeBlockUpdatePacket.STREAM_CODEC,
                DescendreNetwork::handleCubeBlockUpdate
        );

        registrar.playToClient(
                ClientboundForgetCubePacket.TYPE,
                ClientboundForgetCubePacket.STREAM_CODEC,
                DescendreNetwork::handleForgetCube
        );
    }

    // ---------- Handlers (exécutés sur le client) ----------

    private static void handleCubeData(ClientboundCubeDataPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            DescendreClientCubeCache.get().putFromPacket(packet);
            System.out.println("[CLIENT] Received cube " + packet.cubePos()
                    + " palette=" + packet.paletteIds().length
                    + " cacheSize=" + DescendreClientCubeCache.get().size());
        });
    }

    private static void handleCubeBlockUpdate(ClientboundCubeBlockUpdatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            DescendreClientCubeCache.get().updateBlock(packet.pos(), packet.resolveState());
        });
    }

    private static void handleForgetCube(ClientboundForgetCubePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            DescendreClientCubeCache.get().forget(packet.cubePos());
            System.out.println("[CLIENT] Forget cube " + packet.cubePos()
                    + " cacheSize=" + DescendreClientCubeCache.get().size());
        });
    }

    // ---------- Helpers d'envoi (côté serveur) ----------

    public static void sendToPlayer(ServerPlayer player, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }
}