package com.moakiee.ae2lt.network;

import com.moakiee.ae2lt.client.ClientNetworkPacketHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record FrequencyResponsePacket(int responseCode) {

    public static final int REQUIRE_PASSWORD = 1;
    public static final int NO_PERMISSION = 2;
    public static final int INVALID_FREQUENCY = 3;
    public static final int REJECTED = 4;
    public static final int FREQUENCY_IN_USE = 5;

    public static void encode(FrequencyResponsePacket pkt, FriendlyByteBuf buf) {
        buf.writeInt(pkt.responseCode);
    }

    public static FrequencyResponsePacket decode(FriendlyByteBuf buf) {
        return new FrequencyResponsePacket(buf.readInt());
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

    public static void handle(FrequencyResponsePacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        var ctx = ctxSupplier.get();
        Component message = pkt.toMessage();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> ClientNetworkPacketHandlers.handleFrequencyResponse(message)));
        ctx.setPacketHandled(true);
    }
}

