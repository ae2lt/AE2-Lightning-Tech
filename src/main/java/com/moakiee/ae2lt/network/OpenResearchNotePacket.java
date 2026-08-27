package com.moakiee.ae2lt.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

public record OpenResearchNotePacket(ItemStack book) {
    public void write(FriendlyByteBuf buf) {
        buf.writeItem(book);
    }

    public static OpenResearchNotePacket decode(FriendlyByteBuf buf) {
        return new OpenResearchNotePacket(buf.readItem());
    }

    public static void handle(OpenResearchNotePacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> ResearchNoteClientBridge.open(packet.book()));
        ctx.setPacketHandled(true);
    }
}
