package fr.descendre.mixin.collision;

import fr.descendre.network.ClientboundLevelEventCubicPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hook ServerLevel.levelEvent pour rediriger les événements de niveau (particules
 * de cassage, etc.) vers notre packet custom quand Y est hors de la range vanilla.
 *
 * Le packet vanilla ClientboundLevelEventPacket utilise BlockPos.STREAM_CODEC qui
 * tronque Y à 12 bits, donc inutilisable pour Y=-10000.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelLevelEventMixin {

    @Inject(
            method = "levelEvent(Lnet/minecraft/world/entity/Entity;ILnet/minecraft/core/BlockPos;I)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void descendre$redirectLevelEventCubic(
            Entity except,
            int eventId,
            BlockPos pos,
            int data,
            CallbackInfo ci
    ) {
        ServerLevel self = (ServerLevel) (Object) this;

        // Range vanilla : on laisse vanilla faire (le packet standard marche)
        if (pos.getY() >= self.getMinY() && pos.getY() <= self.getMaxY()) return;

        // Y hors range : on doit envoyer notre packet custom à tous les joueurs proches (rayon 64)
        ClientboundLevelEventCubicPacket packet = ClientboundLevelEventCubicPacket.of(eventId, pos, data, false);
        Player exceptPlayer = (except instanceof Player p) ? p : null;

        for (ServerPlayer player : self.players()) {
            if (player == exceptPlayer) continue;

            double dx = player.getX() - pos.getX();
            double dy = player.getY() - pos.getY();
            double dz = player.getZ() - pos.getZ();
            if (dx * dx + dy * dy + dz * dz > 64.0 * 64.0) continue;

            PacketDistributor.sendToPlayer(player, packet);
        }

        ci.cancel();
    }
}