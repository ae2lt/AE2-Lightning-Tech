package com.moakiee.ae2lt.network;
import java.util.function.Supplier;
import net.minecraftforge.network.NetworkEvent;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraft.server.level.ServerPlayer;

public record DashPacket() {
public static DashPacket decode(FriendlyByteBuf buf) {
        return new DashPacket();
    }

    public void write(FriendlyByteBuf buf) {}

    public static void handle(DashPacket payload, Supplier<NetworkEvent.Context> context) {
        var ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
        if (player != null) {
                com.moakiee.ae2lt.celestweave.module.DashSubmodule.applyDash(
                        player,
                        player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET));
            }
        });
        ctx.setPacketHandled(true);
    }
}
