package com.moakiee.ae2lt.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server-to-client cue for one stage of the hyperdimensional Pigmee reveal.
 *
 * <p>Vanilla entity event 35 only displays an activation item when its target is the local
 * player. The ritual reward is an item entity, so its three material activations need an explicit
 * client packet.</p>
 */
public record RitualItemBurstPacket(int entityId, byte stage) implements CustomPacketPayload {
    public static final byte PIGMEE_CORE = 0;
    public static final byte UNDYING_MODULE = 1;
    public static final byte PHASE_LOCK_MODULE = 2;

    public static final Type<RitualItemBurstPacket> TYPE =
            new Type<>(NetworkInit.id("ritual_item_burst"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RitualItemBurstPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    RitualItemBurstPacket::entityId,
                    ByteBufCodecs.BYTE,
                    RitualItemBurstPacket::stage,
                    RitualItemBurstPacket::new);

    @Override
    public Type<RitualItemBurstPacket> type() {
        return TYPE;
    }

    public static void handle(RitualItemBurstPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> RitualItemBurstClientBridge.show(packet));
    }
}
