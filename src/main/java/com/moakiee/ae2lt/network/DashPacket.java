package com.moakiee.ae2lt.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record DashPacket() implements CustomPacketPayload {

    public static final Type<DashPacket> TYPE =
            new Type<>(NetworkInit.id("dash"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DashPacket> STREAM_CODEC =
            StreamCodec.ofMember(DashPacket::write, DashPacket::decode);

    @Override
    public Type<DashPacket> type() {
        return TYPE;
    }

    public static DashPacket decode(RegistryFriendlyByteBuf buf) {
        return new DashPacket();
    }

    public void write(RegistryFriendlyByteBuf buf) {}

    public static void handle(DashPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                com.moakiee.ae2lt.celestweave.module.DashSubmodule.applyDash(
                        player,
                        player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET));
            }
        });
    }
}
