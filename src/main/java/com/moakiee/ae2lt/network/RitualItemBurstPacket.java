package com.moakiee.ae2lt.network;
import java.util.function.Supplier;
import net.minecraftforge.network.NetworkEvent;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Server-to-client cue for one stage of the hyperdimensional Pigmee reveal.
 *
 * <p>Vanilla entity event 35 only displays an activation item when its target is the local
 * player. The ritual reward is an item entity, so its three material activations need an explicit
 * client packet.</p>
 */
public record RitualItemBurstPacket(int entityId, byte stage) {
    public static final byte PIGMEE_CORE = 0;
    public static final byte UNDYING_MODULE = 1;
    public static final byte PHASE_LOCK_MODULE = 2;

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeByte(stage);
    }

    public static RitualItemBurstPacket decode(FriendlyByteBuf buf) {
        return new RitualItemBurstPacket(buf.readVarInt(), buf.readByte());
    }

    public static void handle(RitualItemBurstPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> RitualItemBurstClientBridge.show(packet));
    }
}
