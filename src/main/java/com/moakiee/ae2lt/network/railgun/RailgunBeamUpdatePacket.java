package com.moakiee.ae2lt.network.railgun;

import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.moakiee.ae2lt.network.NetworkInit;

/**
 * Server to tracking client: keepalive/update packet for an active beam owned
 * by player {@code shooterId}. {@code active=false} signals beam stop.
 */
public record RailgunBeamUpdatePacket(UUID shooterId, Vec3 from, Vec3 to, boolean active)
        implements CustomPacketPayload {

    public static final Type<RailgunBeamUpdatePacket> TYPE =
            new Type<>(NetworkInit.id("railgun_beam_update"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RailgunBeamUpdatePacket> STREAM_CODEC =
            StreamCodec.ofMember(RailgunBeamUpdatePacket::write, RailgunBeamUpdatePacket::decode);

    @Override
    public Type<RailgunBeamUpdatePacket> type() {
        return TYPE;
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(shooterId);
        buf.writeDouble(from.x); buf.writeDouble(from.y); buf.writeDouble(from.z);
        buf.writeDouble(to.x); buf.writeDouble(to.y); buf.writeDouble(to.z);
        buf.writeBoolean(active);
    }

    public static RailgunBeamUpdatePacket decode(RegistryFriendlyByteBuf buf) {
        return new RailgunBeamUpdatePacket(
                buf.readUUID(),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                buf.readBoolean());
    }

    public static void handle(RailgunBeamUpdatePacket p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> RailgunClientBridge.beamUpdate(p));
    }

    /** Compile-time guard. */
    @SuppressWarnings("unused") private static final Object _IMPORT_GUARD = ByteBufCodecs.BYTE;
}
