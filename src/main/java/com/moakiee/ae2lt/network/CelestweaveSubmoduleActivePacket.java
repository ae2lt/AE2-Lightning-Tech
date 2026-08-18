package com.moakiee.ae2lt.network;

import java.util.function.Supplier;
import java.util.UUID;

import com.moakiee.ae2lt.client.ClientNetworkPacketHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

public record CelestweaveSubmoduleActivePacket(UUID armorId, String submoduleId, boolean active) {
    public static CelestweaveSubmoduleActivePacket decode(FriendlyByteBuf buf) {
        return new CelestweaveSubmoduleActivePacket(
                buf.readUUID(),
                buf.readUtf(128),
                buf.readBoolean());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(armorId);
        buf.writeUtf(submoduleId, 128);
        buf.writeBoolean(active);
    }

    public static void handle(CelestweaveSubmoduleActivePacket payload, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> ClientNetworkPacketHandlers.handleCelestweaveSubmoduleActive(payload)));
        ctx.setPacketHandled(true);
    }
}
