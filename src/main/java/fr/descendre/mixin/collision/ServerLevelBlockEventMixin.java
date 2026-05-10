package fr.descendre.mixin.collision;

import fr.descendre.network.ClientboundCubicBlockEventPacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Hook ServerLevel.runBlockEvents pour rediriger les block events (animations
 * comme l'ouverture des coffres, son des notes blocks, etc.) vers notre packet
 * custom quand Y est hors de la range vanilla.
 *
 * Le packet vanilla ClientboundBlockEventPacket utilise BlockPos.STREAM_CODEC
 * qui tronque Y à 12 bits — inutilisable pour Y=-10000.
 *
 * On redirige l'appel à PlayerList.broadcast en interceptant le packet vanilla
 * et en envoyant à la place notre packet custom.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelBlockEventMixin {

    /**
     * Redirige l'appel PlayerList.broadcast(...) dans runBlockEvents.
     * Si le packet est un ClientboundBlockEventPacket à Y hors range,
     * on le remplace par notre packet custom.
     */
    @Redirect(
            method = "runBlockEvents",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/players/PlayerList;broadcast(Lnet/minecraft/world/entity/player/Player;DDDDLnet/minecraft/resources/ResourceKey;Lnet/minecraft/network/protocol/Packet;)V"
            )
    )
    private void descendre$redirectBlockEventBroadcast(
            net.minecraft.server.players.PlayerList playerList,
            Player except,
            double x,
            double y,
            double z,
            double radius,
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
            Packet<?> packet
    ) {
        ServerLevel self = (ServerLevel) (Object) this;

        // Si Y dans la range vanilla : laisse vanilla faire son broadcast normal
        if (y >= self.getMinY() && y <= self.getMaxY()) {
            playerList.broadcast(except, x, y, z, radius, dimension, packet);
            return;
        }

        // Sinon : packet vanilla rejeté car position tronquée. On envoie notre packet.
        if (!(packet instanceof ClientboundBlockEventPacket vanillaPacket)) {
            // Pas un block event packet (cas inattendu) : on broadcast quand même
            playerList.broadcast(except, x, y, z, radius, dimension, packet);
            return;
        }

        // On reconstitue depuis les coords passées (qui sont en double, exactes)
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        int iz = (int) Math.floor(z);

        ClientboundCubicBlockEventPacket cubicPacket = ClientboundCubicBlockEventPacket.of(
                ix, iy, iz,
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.getId(vanillaPacket.getBlock()),
                vanillaPacket.getB0(),
                vanillaPacket.getB1()
        );

        for (ServerPlayer player : self.players()) {
            if (player == except) continue;
            double dx = player.getX() - x;
            double dy = player.getY() - y;
            double dz = player.getZ() - z;
            if (dx * dx + dy * dy + dz * dz > radius * radius) continue;
            PacketDistributor.sendToPlayer(player, cubicPacket);
        }
    }
}