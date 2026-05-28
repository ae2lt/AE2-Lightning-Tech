package com.moakiee.ae2lt.network.railgun;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.moakiee.ae2lt.network.NetworkInit;

/**
 * Server -> client: chain-jump visual update for the held left-click beam.
 *
 * <p>The beam itself is rendered every frame via {@link RailgunBeamUpdatePacket}
 * keepalive state. This packet is fired only when a chain actually triggers
 * (throttled to ~4 jumps/sec by {@code railgunBeamChainThrottleTicks}) so the
 * client can spawn a one-shot lightning arc from the primary impact through
 * each chained target. Without this packet the beam would hit chained enemies
 * silently with no visual feedback, which is the exact bug we're patching.
 *
 * @param shooterId  firing player; used to dedupe / locate the source for FX
 * @param chainPath  flat list of segment endpoints — pairs (i, i+1) form one
 *                   arc segment; empty if no chain triggered this tick
 * @param firstHit   primary impact point (entity's hit center) — used as the
 *                   origin for the first arc and for impact sparks
 */
public record RailgunBeamChainFxPacket(UUID shooterId, Vec3 firstHit, List<Vec3> chainPath)
        implements CustomPacketPayload {

    public static final Type<RailgunBeamChainFxPacket> TYPE =
            new Type<>(NetworkInit.id("railgun_beam_chain_fx"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RailgunBeamChainFxPacket> STREAM_CODEC =
            StreamCodec.ofMember(RailgunBeamChainFxPacket::write, RailgunBeamChainFxPacket::decode);

    @Override
    public Type<RailgunBeamChainFxPacket> type() {
        return TYPE;
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(shooterId);
        buf.writeDouble(firstHit.x);
        buf.writeDouble(firstHit.y);
        buf.writeDouble(firstHit.z);
        buf.writeVarInt(chainPath.size());
        for (Vec3 v : chainPath) {
            buf.writeDouble(v.x);
            buf.writeDouble(v.y);
            buf.writeDouble(v.z);
        }
    }

    public static RailgunBeamChainFxPacket decode(RegistryFriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        Vec3 first = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        int n = buf.readVarInt();
        List<Vec3> path = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            path.add(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));
        }
        return new RailgunBeamChainFxPacket(id, first, path);
    }

    public static void handle(RailgunBeamChainFxPacket p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> RailgunClientBridge.beamChainFx(p));
    }

    /** Compile-time guard. */
    @SuppressWarnings("unused") private static final Object _IMPORT_GUARD = ByteBufCodecs.BYTE;
}
