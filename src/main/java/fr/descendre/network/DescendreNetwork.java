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
import fr.descendre.core.DescendreHeight;
import net.minecraft.server.level.ServerLevel;

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

        registrar.playToServer(
                ServerboundCubicBlockActionPacket.TYPE,
                ServerboundCubicBlockActionPacket.STREAM_CODEC,
                DescendreNetwork::handleCubicBlockAction
        );

    }

    private static void handleCubicBlockAction(ServerboundCubicBlockActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof net.minecraft.server.level.ServerPlayer player)) return;
            net.minecraft.server.level.ServerLevel level = player.level();
            net.minecraft.core.BlockPos pos = packet.pos();

            // Validation 1 : range Descendre
            if (!fr.descendre.core.DescendreHeight.isInsideInternalRange(pos.getY())) return;

            // Validation 2 : portée du joueur (~6 blocs en créatif)
            double maxReach = player.blockInteractionRange() + 1.0;
            double dx = pos.getX() + 0.5 - player.getX();
            double dy = pos.getY() + 0.5 - player.getY();
            double dz = pos.getZ() + 0.5 - player.getZ();
            if (dx * dx + dy * dy + dz * dz > maxReach * maxReach) {
                System.out.println("[ACTION] reject (too far) pos=" + pos);
                return;
            }

            fr.descendre.world.cube.CubeMap map = fr.descendre.server.DescendreCubeManager.get(level);

            if (packet.isPlace()) {
                // Vérifie que la position est libre
                net.minecraft.world.level.block.state.BlockState existing = map.getBlock(pos);
                if (existing != null && !existing.isAir()) return;
                map.setBlock(pos, packet.resolveState());
            } else {
                map.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            }
        });
    }

    // ---------- Handlers (exécutés sur le client) ----------

    private static void handleCubeData(ClientboundCubeDataPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            DescendreClientCubeCache.get().putFromPacket(packet);

        });
    }

    private static void handleCubeBlockUpdate(ClientboundCubeBlockUpdatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            DescendreClientCubeCache.get().updateBlock(packet.pos(), packet.resolveState());
            System.out.println("[CLIENT-UPDATE] pos=" + packet.pos() + " state=" + packet.resolveState());
        });
    }

    private static void handleForgetCube(ClientboundForgetCubePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            DescendreClientCubeCache.get().forget(packet.cubePos());
        });
    }

    // ---------- Helpers d'envoi (côté serveur) ----------

    public static void sendToPlayer(ServerPlayer player, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }
}