package com.moakiee.ae2lt.network;
import java.util.function.Supplier;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;

import com.moakiee.ae2lt.celestweave.CelestweaveArmorState;

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
        ctx.enqueueWork(() -> CelestweaveArmorState.markClientActive(
                payload.armorId(),
                payload.submoduleId(),
                payload.active()));
    }
}
