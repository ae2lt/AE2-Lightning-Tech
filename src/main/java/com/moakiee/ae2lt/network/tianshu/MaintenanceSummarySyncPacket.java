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

public record MaintenanceSummarySyncPacket(
        int containerId, int selectionRevision, long revision,
        boolean overflow, List<Entry> entries)
        implements CustomPacketPayload {
    public static final Type<MaintenanceSummarySyncPacket> TYPE =
            new Type<>(NetworkInit.id("maintenance_summary_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MaintenanceSummarySyncPacket> STREAM_CODEC =
            StreamCodec.ofMember(MaintenanceSummarySyncPacket::write, MaintenanceSummarySyncPacket::decode);

    public MaintenanceSummarySyncPacket {
        entries = List.copyOf(entries);
        TianshuPacketLimits.requireListSize("maintenance summary", entries.size());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeVarInt(selectionRevision);
        buf.writeVarLong(revision);
        buf.writeBoolean(overflow);
        buf.writeVarInt(entries.size());
        for (var entry : entries) {
            AEKey.STREAM_CODEC.encode(buf, entry.key());
            buf.writeEnum(entry.status());
            buf.writeLong(entry.globalReserve());
            buf.writeEnum(entry.globalMode());
            buf.writeBoolean(entry.ruleReserveOverflow());
        }
    }

    private static MaintenanceSummarySyncPacket decode(RegistryFriendlyByteBuf buf) {
        int container = buf.readVarInt();
        int selectionRevision = buf.readVarInt();
        long revision = buf.readVarLong();
        boolean overflow = buf.readBoolean();
        int size = TianshuPacketLimits.requireDecodedListSize(
                "maintenance summary", buf.readVarInt());
        var entries = new ArrayList<Entry>(size);
        for (int i = 0; i < size; i++) entries.add(new Entry(
                AEKey.STREAM_CODEC.decode(buf), buf.readEnum(InventoryMaintenanceStatus.class),
                buf.readLong(), buf.readEnum(ReservedStockMatchMode.class), buf.readBoolean()));
        return new MaintenanceSummarySyncPacket(
                container, selectionRevision, revision, overflow, entries);
    }

    @Override public Type<MaintenanceSummarySyncPacket> type() { return TYPE; }

    public static void handle(MaintenanceSummarySyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof TianshuPatternEncodingTermMenu menu
                    && menu.containerId == packet.containerId()) {
                menu.receiveMaintenanceSummary(
                        packet.selectionRevision(), packet.revision(),
                        packet.overflow(), packet.entries());
            }
        });
    }

    public record Entry(AEKey key, InventoryMaintenanceStatus status,
                        long globalReserve, ReservedStockMatchMode globalMode,
                        boolean ruleReserveOverflow) { }
}
