package fr.descendre.network;

import fr.descendre.DescendreMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Packet client→serveur pour les actions sur les blocs cubic.
 *
 * Contrairement aux packets vanilla qui tronquent Y à 12 bits, on encode
 * toutes les positions en int32 pour supporter Y jusqu'à -15000.
 *
 * Pour le placement, on inclut tout le contexte du BlockHitResult original
 * afin que le serveur puisse reconstruire un UseOnContext complet, et ainsi
 * appeler BlockItem.useOn() qui gère : orientation, connexions voisins, GUI, sons...
 */
public record ServerboundCubicBlockActionPacket(
        // Position du bloc cible (où on clique)
        int targetX, int targetY, int targetZ,
        // Face cliquée
        Direction face,
        // Position dans la face (0.0 à 1.0)
        float hitX, float hitY, float hitZ,
        // Main utilisée
        InteractionHand hand,
        // true = placement, false = cassage
        boolean isPlace,
        // Pour le placement : quel item (blockStateId du bloc à placer)
        int blockStateId
) implements CustomPacketPayload {

    public static final Type<ServerboundCubicBlockActionPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(DescendreMod.MODID, "cubic_block_action")
    );

    public static final StreamCodec<ByteBuf, ServerboundCubicBlockActionPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ServerboundCubicBlockActionPacket decode(ByteBuf buf) {
                    int tx = buf.readInt();
                    int ty = buf.readInt();
                    int tz = buf.readInt();
                    Direction face = Direction.values()[buf.readByte()];
                    float hx = buf.readFloat();
                    float hy = buf.readFloat();
                    float hz = buf.readFloat();
                    InteractionHand hand = buf.readBoolean() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
                    boolean isPlace = buf.readBoolean();
                    int stateId = ByteBufCodecs.VAR_INT.decode(buf);
                    return new ServerboundCubicBlockActionPacket(tx, ty, tz, face, hx, hy, hz, hand, isPlace, stateId);
                }

                @Override
                public void encode(ByteBuf buf, ServerboundCubicBlockActionPacket p) {
                    buf.writeInt(p.targetX);
                    buf.writeInt(p.targetY);
                    buf.writeInt(p.targetZ);
                    buf.writeByte(p.face.ordinal());
                    buf.writeFloat(p.hitX);
                    buf.writeFloat(p.hitY);
                    buf.writeFloat(p.hitZ);
                    buf.writeBoolean(p.hand == InteractionHand.MAIN_HAND);
                    buf.writeBoolean(p.isPlace);
                    ByteBufCodecs.VAR_INT.encode(buf, p.blockStateId);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Crée un packet de cassage depuis un BlockHitResult. */
    public static ServerboundCubicBlockActionPacket breakFrom(BlockHitResult hit, InteractionHand hand) {
        BlockPos pos = hit.getBlockPos();
        Vec3 loc = hit.getLocation();
        float hx = (float)(loc.x - pos.getX());
        float hy = (float)(loc.y - pos.getY());
        float hz = (float)(loc.z - pos.getZ());
        return new ServerboundCubicBlockActionPacket(
                pos.getX(), pos.getY(), pos.getZ(),
                hit.getDirection(), hx, hy, hz,
                hand, false,
                Block.BLOCK_STATE_REGISTRY.getId(Blocks.AIR.defaultBlockState())
        );
    }

    /** Crée un packet de placement depuis un BlockHitResult et le state à poser. */
    public static ServerboundCubicBlockActionPacket placeFrom(BlockHitResult hit, InteractionHand hand, BlockState state) {
        BlockPos pos = hit.getBlockPos();
        Vec3 loc = hit.getLocation();
        float hx = (float)(loc.x - pos.getX());
        float hy = (float)(loc.y - pos.getY());
        float hz = (float)(loc.z - pos.getZ());
        return new ServerboundCubicBlockActionPacket(
                pos.getX(), pos.getY(), pos.getZ(),
                hit.getDirection(), hx, hy, hz,
                hand, true,
                Block.BLOCK_STATE_REGISTRY.getId(state)
        );
    }

    /** Reconstruit le BlockHitResult original (avec vraies coordonnées int32). */
    public BlockHitResult toHitResult() {
        BlockPos pos = new BlockPos(targetX, targetY, targetZ);
        Vec3 location = new Vec3(targetX + hitX, targetY + hitY, targetZ + hitZ);
        return new BlockHitResult(location, face, pos, false);
    }

    public BlockState resolveState() {
        BlockState state = Block.BLOCK_STATE_REGISTRY.byId(blockStateId);
        return state != null ? state : Blocks.AIR.defaultBlockState();
    }
}