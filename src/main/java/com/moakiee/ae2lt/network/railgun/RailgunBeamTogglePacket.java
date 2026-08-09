package com.moakiee.ae2lt.network.railgun;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import com.moakiee.ae2lt.item.railgun.ElectromagneticRailgunItem;
import com.moakiee.ae2lt.logic.railgun.RailgunBeamService;

/** Client to server: toggle left-beam firing on/off. */
public record RailgunBeamTogglePacket(boolean firing, InteractionHand hand) {
    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(firing);
        buf.writeEnum(hand);
    }

    public static RailgunBeamTogglePacket decode(FriendlyByteBuf buf) {
        return new RailgunBeamTogglePacket(buf.readBoolean(), buf.readEnum(InteractionHand.class));
    }

    public static void handle(RailgunBeamTogglePacket pkt, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer p = ctx.getSender();
            if (p == null) return;
            ItemStack stack = p.getItemInHand(pkt.hand());
            if (pkt.firing() && !(stack.getItem() instanceof ElectromagneticRailgunItem)) return;
            RailgunBeamService.setFiring(p, pkt.hand(), pkt.firing());
        });
        ctx.setPacketHandled(true);
    }
}
