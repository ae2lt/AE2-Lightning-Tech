package com.moakiee.ae2lt.network.tianshu;

import appeng.api.stacks.AEKey;
import com.moakiee.ae2lt.logic.tianshu.maintenance.InventoryMaintenanceStatus;
import com.moakiee.ae2lt.logic.tianshu.maintenance.ReservedStockMatchMode;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import com.moakiee.ae2lt.network.NetworkInit;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record MaintenanceSummarySyncPacket(int containerId, List<Entry> entries)
        implements CustomPacketPayload {
    public static final Type<MaintenanceSummarySyncPacket> TYPE =
            new Type<>(NetworkInit.id("maintenance_summary_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MaintenanceSummarySyncPacket> STREAM_CODEC =
            StreamCodec.ofMember(MaintenanceSummarySyncPacket::write, MaintenanceSummarySyncPacket::decode);

    public MaintenanceSummarySyncPacket { entries = List.copyOf(entries); }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeVarInt(entries.size());
        for (var entry : entries) {
            AEKey.STREAM_CODEC.encode(buf, entry.key());
            buf.writeEnum(entry.status());
            buf.writeLong(entry.globalReserve());
            buf.writeEnum(entry.globalMode());
        }
    }

    private static MaintenanceSummarySyncPacket decode(RegistryFriendlyByteBuf buf) {
        int container = buf.readVarInt();
        int size = Math.min(4096, buf.readVarInt());
        var entries = new ArrayList<Entry>(size);
        for (int i = 0; i < size; i++) entries.add(new Entry(
                AEKey.STREAM_CODEC.decode(buf), buf.readEnum(InventoryMaintenanceStatus.class),
                buf.readLong(), buf.readEnum(ReservedStockMatchMode.class)));
        return new MaintenanceSummarySyncPacket(container, entries);
    }

    @Override public Type<MaintenanceSummarySyncPacket> type() { return TYPE; }

    public static void handle(MaintenanceSummarySyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof TianshuPatternEncodingTermMenu menu
                    && menu.containerId == packet.containerId()) menu.receiveMaintenanceSummary(packet.entries());
        });
    }

    public record Entry(AEKey key, InventoryMaintenanceStatus status,
                        long globalReserve, ReservedStockMatchMode globalMode) { }
}
