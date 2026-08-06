package com.moakiee.ae2lt.network;

import com.moakiee.ae2lt.client.gui.FrequencyScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record FrequencyResponsePacket(int responseCode) implements CustomPacketPayload {

    public static final int REQUIRE_PASSWORD = 1;
    public static final int NO_PERMISSION = 2;
    public static final int INVALID_FREQUENCY = 3;
    public static final int REJECTED = 4;
    public static final int FREQUENCY_IN_USE = 5;

    public static final Type<FrequencyResponsePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("ae2lt", "frequency_response"));

    public static final StreamCodec<FriendlyByteBuf, FrequencyResponsePacket> STREAM_CODEC =
            StreamCodec.of(FrequencyResponsePacket::encode, FrequencyResponsePacket::decode);

    private static void encode(FriendlyByteBuf buf, FrequencyResponsePacket pkt) {
        buf.writeInt(pkt.responseCode);
    }

    private static FrequencyResponsePacket decode(FriendlyByteBuf buf) {
        return new FrequencyResponsePacket(buf.readInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private Component toMessage() {
        return switch (responseCode) {
            case REQUIRE_PASSWORD -> Component.translatable("ae2lt.gui.error.require_password");
            case NO_PERMISSION -> Component.translatable("ae2lt.gui.error.no_permission");
            case INVALID_FREQUENCY -> Component.translatable("ae2lt.gui.error.invalid_frequency");
            case FREQUENCY_IN_USE -> Component.translatable("ae2lt.gui.error.frequency_in_use");
            default -> Component.translatable("ae2lt.gui.error.rejected");
        };
    }

    public static void handle(FrequencyResponsePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof LocalPlayer player)) return;
            Component message = pkt.toMessage();
            // Container screens cover the hotbar / action-bar region, so
            // a stock {@code displayClientMessage(..., true)} is painted
            // underneath the GUI and the player never sees it. Route
            // the toast into the FrequencyScreen's inline banner when
            // it's open, and fall back to the action-bar only when it
            // isn't (e.g. an error arrives after the user closed the
            // GUI). Chat stays untouched either way.
            if (Minecraft.getInstance().screen instanceof FrequencyScreen fs) {
                fs.showInlineError(message);
            } else {
                player.displayClientMessage(message, true);
            }
        });
    }
}
