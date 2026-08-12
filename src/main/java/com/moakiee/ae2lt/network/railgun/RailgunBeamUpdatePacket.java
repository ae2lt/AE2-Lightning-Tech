package com.moakiee.ae2lt.network.railgun;
import java.util.function.Supplier;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;


/**
 * Server to tracking client: keepalive/update packet for an active beam owned
 * by player {@code shooterId}. {@code active=false} signals beam stop.
 */
public record RailgunBeamUpdatePacket(UUID shooterId, Vec3 from, Vec3 to, boolean active) {
public void write(FriendlyByteBuf buf) {
        buf.writeUUID(shooterId);
        buf.writeDouble(from.x); buf.writeDouble(from.y); buf.writeDouble(from.z);
        buf.writeDouble(to.x); buf.writeDouble(to.y); buf.writeDouble(to.z);
        buf.writeBoolean(active);
    }

    public static RailgunBeamUpdatePacket decode(FriendlyByteBuf buf) {
        return new RailgunBeamUpdatePacket(
                buf.readUUID(),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                buf.readBoolean());
    }

    public static void handle(RailgunBeamUpdatePacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> RailgunClientBridge.beamUpdate(p));
    }

    /** Compile-time guard. */
}
