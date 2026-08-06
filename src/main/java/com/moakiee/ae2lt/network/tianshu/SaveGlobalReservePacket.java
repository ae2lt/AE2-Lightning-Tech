package com.moakiee.ae2lt.network.tianshu;

import appeng.api.stacks.AEKey;
import com.moakiee.ae2lt.logic.tianshu.maintenance.ReservedStockMatchMode;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import com.moakiee.ae2lt.network.NetworkInit;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SaveGlobalReservePacket(
        int containerId, int selectionRevision, AEKey key,
        long amount, ReservedStockMatchMode mode)
        implements CustomPacketPayload {
    public static final Type<SaveGlobalReservePacket> TYPE =
            new Type<>(NetworkInit.id("save_global_reserve"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SaveGlobalReservePacket> STREAM_CODEC =
            StreamCodec.ofMember(SaveGlobalReservePacket::write, SaveGlobalReservePacket::decode);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeVarInt(selectionRevision);
        AEKey.STREAM_CODEC.encode(buf, key);
        buf.writeLong(amount);
        buf.writeEnum(mode);
    }

    private static SaveGlobalReservePacket decode(RegistryFriendlyByteBuf buf) {
        return new SaveGlobalReservePacket(buf.readVarInt(), buf.readVarInt(),
                AEKey.STREAM_CODEC.decode(buf),
                buf.readLong(), buf.readEnum(ReservedStockMatchMode.class));
    }

    @Override public Type<SaveGlobalReservePacket> type() { return TYPE; }

    public static void handle(SaveGlobalReservePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof TianshuPatternEncodingTermMenu menu
                    && menu.containerId == packet.containerId()) menu.saveGlobalReserve(packet);
        });
    }
}
