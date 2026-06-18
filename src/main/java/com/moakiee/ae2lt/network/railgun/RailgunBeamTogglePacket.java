package com.moakiee.ae2lt.network.railgun;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.moakiee.ae2lt.item.railgun.ElectromagneticRailgunItem;
import com.moakiee.ae2lt.logic.railgun.RailgunBeamService;
import com.moakiee.ae2lt.network.NetworkInit;

/** Client to server: toggle left-beam firing on/off. */
public record RailgunBeamTogglePacket(boolean firing, InteractionHand hand) implements CustomPacketPayload {

    public static final Type<RailgunBeamTogglePacket> TYPE =
            new Type<>(NetworkInit.id("railgun_beam_toggle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RailgunBeamTogglePacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, RailgunBeamTogglePacket::firing,
                    ByteBufCodecs.idMapper(i -> InteractionHand.values()[i], InteractionHand::ordinal),
                    RailgunBeamTogglePacket::hand,
                    RailgunBeamTogglePacket::new);

    @Override
    public Type<RailgunBeamTogglePacket> type() {
        return TYPE;
    }

    public static void handle(RailgunBeamTogglePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer p)) return;
            ItemStack stack = p.getItemInHand(pkt.hand());
            if (pkt.firing() && !(stack.getItem() instanceof ElectromagneticRailgunItem)) return;
            RailgunBeamService.setFiring(p, pkt.hand(), pkt.firing());
        });
    }
}
