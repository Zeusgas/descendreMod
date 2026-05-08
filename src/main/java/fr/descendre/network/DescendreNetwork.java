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
            net.minecraft.core.BlockPos targetPos = new net.minecraft.core.BlockPos(
                    packet.targetX(), packet.targetY(), packet.targetZ()
            );

            // Validation : spectateur, range, portée
            if (player.gameMode.getGameModeForPlayer() == net.minecraft.world.level.GameType.SPECTATOR) return;
            if (!fr.descendre.core.DescendreHeight.isInsideInternalRange(targetPos.getY())) return;
            double maxReach = player.blockInteractionRange() + 1.0;
            double dx = targetPos.getX() + 0.5 - player.getX();
            double dy = targetPos.getY() + 0.5 - player.getY();
            double dz = targetPos.getZ() + 0.5 - player.getZ();
            if (dx * dx + dy * dy + dz * dz > maxReach * maxReach) return;

            fr.descendre.world.cube.CubeMap map = fr.descendre.server.DescendreCubeManager.get(level);

            if (!packet.isPlace()) {
                // CASSAGE
                net.minecraft.world.level.block.state.BlockState existing = map.getBlock(targetPos);
                if (existing == null || existing.isAir()) return;
                map.setBlock(targetPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                if (player.gameMode.getGameModeForPlayer() == net.minecraft.world.level.GameType.SURVIVAL) {
                    net.minecraft.world.level.block.Block.dropResources(existing, level, targetPos, null, player, player.getMainHandItem());
                }
            } else {
                // === PLACEMENT / INTERACTION ===
                if (player.gameMode.getGameModeForPlayer() == net.minecraft.world.level.GameType.ADVENTURE) return;

                net.minecraft.world.phys.BlockHitResult hitResult = packet.toHitResult();
                net.minecraft.world.item.ItemStack stack = player.getItemInHand(packet.hand());

                // Exécute l'opération dans un contexte cubic :
                // Tous les getBlockState/setBlock appels par Block.use() seront redirigés vers CubeMap
                fr.descendre.world.DescendreCubeLevel.runWithContext(level, map, () -> {
                    // 1. D'abord, interaction avec le bloc visé (porte, coffre, levier...)
                    net.minecraft.world.level.block.state.BlockState targetState = map.getBlock(targetPos);
                    if (targetState != null && !targetState.isAir() && !player.isShiftKeyDown()) {
                        // Appelle l'interaction avec item d'abord (ouvrir GUI, etc.)
                        net.minecraft.world.InteractionResult useResult = targetState.useItemOn(
                                stack, level, player, packet.hand(), hitResult
                        );
                        if (useResult.consumesAction()) return;

                        // Sinon, interaction sans item
                        net.minecraft.world.InteractionResult plainResult = targetState.useWithoutItem(level, player, hitResult);
                        if (plainResult.consumesAction()) return;

                        // Si pas d'interaction, essayer avec l'item en main
                        if (!(stack.getItem() instanceof net.minecraft.world.item.BlockItem)) {
                            return; // Pas d'item en main, interaction finie
                        }
                    }

                    // 2. Sinon, placement normal
                    if (!(stack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem)) return;

                    net.minecraft.world.item.context.UseOnContext useOnContext =
                            new net.minecraft.world.item.context.UseOnContext(level, player, packet.hand(), stack, hitResult);
                    blockItem.useOn(useOnContext);
                });
            }
        });
    }

    /** Helper : retourne le BlockState à une position (cubic d'abord, vanilla ensuite). */
    private static net.minecraft.world.level.block.state.BlockState getNeighborState(
            fr.descendre.world.cube.CubeMap map,
            net.minecraft.server.level.ServerLevel level,
            net.minecraft.core.BlockPos pos
    ) {
        net.minecraft.world.level.block.state.BlockState cubic = map.getBlock(pos);
        if (cubic != null && !cubic.isAir()) return cubic;
        return level.getBlockState(pos);
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