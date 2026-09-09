package com.moakiee.ae2lt.network.jei;

import com.moakiee.ae2lt.logic.tianshu.terminal.WirelessJeiInventoryPlan;
import com.moakiee.ae2lt.network.NetworkInit;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 0 = unavailable/failed, 1 = offer, 2 = materials delivered to the real player inventory. */
public record WirelessJeiSupplyResultPacket(int containerId, int requestId, int status, List<ItemStack> items)
        implements CustomPacketPayload {
    public static final Type<WirelessJeiSupplyResultPacket> TYPE = new Type<>(NetworkInit.id("wireless_jei_supply_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WirelessJeiSupplyResultPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, WirelessJeiSupplyResultPacket::containerId,
            ByteBufCodecs.VAR_INT, WirelessJeiSupplyResultPacket::requestId,
            ByteBufCodecs.VAR_INT, WirelessJeiSupplyResultPacket::status,
            ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list(WirelessJeiInventoryPlan.MAX_ENTRIES)), WirelessJeiSupplyResultPacket::items,
            WirelessJeiSupplyResultPacket::new);
    @Override public Type<WirelessJeiSupplyResultPacket> type() { return TYPE; }
    public static void handle(WirelessJeiSupplyResultPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (net.neoforged.fml.ModList.get().isLoaded("jei")) {
                com.moakiee.ae2lt.client.JeiWirelessSupplyClient.receive(packet);
            }
        });
    }
}
