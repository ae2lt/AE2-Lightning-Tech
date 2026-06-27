package com.moakiee.ae2lt.network.railgun;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.moakiee.ae2lt.network.NetworkInit;

/**
 * Server to client: a charged shot fired with the listed chain segments. The
 * client uses this to render electric arcs along each segment plus a flash
 * at the first hit.
 *
 * @param shooterId   id of the firing player; the client looks them up to compute the
 *                    gun-barrel position so the visual plasma trail emanates from the
 *                    weapon, not from the eye/screen-center.
 * @param from        the eye position the shot was fired from (server fallback)
 * @param firstHit    the impact location (entity hit position, or block-hit point on miss)
 * @param chainPath   pairs of points (from, to) for each chain segment to render
 * @param tier        charge tier ordinal (0=HV, 1=EHV1, 2=EHV2, 3=EHV3)
 * @param isMax       true on max-tier shots
 * @param soundEnabled true when railgun-specific sounds should play client-side
 * @param impactRadius radius (blocks) of the impact splash AOE for shockwave rendering
 */
public record RailgunFirePacket(
        UUID shooterId,
        Vec3 from,
        Vec3 firstHit,
        List<Vec3> chainPath,
        int tier,
        boolean isMax,
        boolean soundEnabled,
        float impactRadius) implements CustomPacketPayload {

    public static final Type<RailgunFirePacket> TYPE =
            new Type<>(NetworkInit.id("railgun_fire"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RailgunFirePacket> STREAM_CODEC =
            StreamCodec.ofMember(RailgunFirePacket::write, RailgunFirePacket::decode);

    @Override
    public Type<RailgunFirePacket> type() {
        return TYPE;
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(shooterId);
        buf.writeDouble(from.x); buf.writeDouble(from.y); buf.writeDouble(from.z);
        buf.writeDouble(firstHit.x); buf.writeDouble(firstHit.y); buf.writeDouble(firstHit.z);
        buf.writeVarInt(chainPath.size());
        for (Vec3 v : chainPath) {
            buf.writeDouble(v.x); buf.writeDouble(v.y); buf.writeDouble(v.z);
        }
        buf.writeVarInt(tier);
        buf.writeBoolean(isMax);
        buf.writeBoolean(soundEnabled);
        buf.writeFloat(impactRadius);
    }

    public static RailgunFirePacket decode(RegistryFriendlyByteBuf buf) {
        UUID shooterId = buf.readUUID();
        Vec3 from = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        Vec3 first = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        int n = buf.readVarInt();
        List<Vec3> path = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            path.add(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));
        }
        int tier = buf.readVarInt();
        boolean isMax = buf.readBoolean();
        boolean soundEnabled = buf.readBoolean();
        float impactRadius = buf.readFloat();
        return new RailgunFirePacket(shooterId, from, first, path, tier, isMax, soundEnabled, impactRadius);
    }

    public static void handle(RailgunFirePacket p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> RailgunClientBridge.fire(p));
    }

    /** Compile-time guard on unused imports. */
    @SuppressWarnings("unused") private static final Object _IMPORT_GUARD = ByteBufCodecs.BYTE;
}
