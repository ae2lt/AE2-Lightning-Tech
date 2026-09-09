package com.moakiee.ae2lt.network.jei;

import com.moakiee.ae2lt.logic.tianshu.terminal.WirelessJeiInventoryPlan;
import com.moakiee.ae2lt.logic.tianshu.terminal.WirelessJeiSupply;
import com.moakiee.ae2lt.network.NetworkInit;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record WirelessJeiSupplyPacket(int containerId, int requestId, boolean take, List<ItemStack> items)
        implements CustomPacketPayload {
    public static final Type<WirelessJeiSupplyPacket> TYPE = new Type<>(NetworkInit.id("wireless_jei_supply"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WirelessJeiSupplyPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, WirelessJeiSupplyPacket::containerId,
            ByteBufCodecs.VAR_INT, WirelessJeiSupplyPacket::requestId,
            ByteBufCodecs.BOOL, WirelessJeiSupplyPacket::take,
            ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list(WirelessJeiInventoryPlan.MAX_ENTRIES)), WirelessJeiSupplyPacket::items,
            WirelessJeiSupplyPacket::new);
    @Override public Type<WirelessJeiSupplyPacket> type() { return TYPE; }
    public static void handle(WirelessJeiSupplyPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                PacketDistributor.sendToPlayer(player, WirelessJeiSupply.handle(player, packet));
            }
        });
    }
}
