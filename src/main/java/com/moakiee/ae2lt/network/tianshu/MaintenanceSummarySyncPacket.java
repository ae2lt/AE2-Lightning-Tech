package com.moakiee.ae2lt.network.tianshu;
import java.util.function.Supplier;
import appeng.api.stacks.AEKey;
import com.moakiee.ae2lt.logic.tianshu.maintenance.InventoryMaintenanceStatus;
import com.moakiee.ae2lt.logic.tianshu.maintenance.ReservedStockMatchMode;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import com.moakiee.ae2lt.network.NetworkInit;
import java.util.ArrayList;
import java.util.List;
import net.minecraftforge.network.NetworkEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;

public record MaintenanceSummarySyncPacket(
        int containerId, int selectionRevision, long revision,
        boolean overflow, List<Entry> entries) {
public MaintenanceSummarySyncPacket {
        entries = List.copyOf(entries);
        TianshuPacketLimits.requireListSize("maintenance summary", entries.size());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeVarInt(selectionRevision);
        buf.writeVarLong(revision);
        buf.writeBoolean(overflow);
        buf.writeVarInt(entries.size());
        for (var entry : entries) {
            AEKey.writeKey(buf, entry.key());
            buf.writeBoolean(entry.ruleConfigured());
            buf.writeEnum(entry.status());
            buf.writeVarLong(entry.storedAmount());
            buf.writeVarLong(entry.lowerThreshold());
            buf.writeVarLong(entry.upperThreshold());
            buf.writeVarLong(entry.amountPerJob());
            buf.writeLong(entry.globalReserve());
            buf.writeEnum(entry.globalMode());
            buf.writeBoolean(entry.globalReserveConfigured());
            buf.writeBoolean(entry.craftable());
            buf.writeBoolean(entry.ruleReserveOverflow());
        }
    }

    public static MaintenanceSummarySyncPacket decode(FriendlyByteBuf buf) {
        int container = buf.readVarInt();
        int selectionRevision = buf.readVarInt();
        long revision = buf.readVarLong();
        boolean overflow = buf.readBoolean();
        int size = TianshuPacketLimits.requireDecodedListSize(
                "maintenance summary", buf.readVarInt());
        var entries = new ArrayList<Entry>(size);
        for (int i = 0; i < size; i++) entries.add(new Entry(
                AEKey.readKey(buf), buf.readBoolean(),
                buf.readEnum(InventoryMaintenanceStatus.class),
                buf.readVarLong(), buf.readVarLong(), buf.readVarLong(), buf.readVarLong(),
                buf.readLong(), buf.readEnum(ReservedStockMatchMode.class),
                buf.readBoolean(), buf.readBoolean(), buf.readBoolean()));
        return new MaintenanceSummarySyncPacket(
                container, selectionRevision, revision, overflow, entries);
    }
    // 1.20.1: this is a server→client sync; the 1.21 handle ran on the client's menu, so the
    // reception side must be checked and the client player used (ctx.getSender() is null here).
    public static void handle(MaintenanceSummarySyncPacket packet, Supplier<NetworkEvent.Context> context) {
        var ctx = context.get();
        ctx.enqueueWork(() -> {
            if (ctx.getDirection().getReceptionSide().isClient()) {
                var player = Minecraft.getInstance().player;
                if (player != null && player.containerMenu instanceof TianshuPatternEncodingTermMenu menu
                        && menu.containerId == packet.containerId()) {
                    menu.receiveMaintenanceSummary(
                            packet.selectionRevision(), packet.revision(),
                            packet.overflow(), packet.entries());
                }
            }
        });
        ctx.setPacketHandled(true);
    }

    /**
     * Compact, periodically refreshed projection used by both the terminal badges and the
     * inventory-maintenance overview. Reserve-only entries deliberately remain distinguishable
     * from configured rules so they are never injected into the maintainable terminal view.
     */
    public record Entry(
            AEKey key,
            boolean ruleConfigured,
            InventoryMaintenanceStatus status,
            long storedAmount,
            long lowerThreshold,
            long upperThreshold,
            long amountPerJob,
            long globalReserve,
            ReservedStockMatchMode globalMode,
            boolean globalReserveConfigured,
            boolean craftable,
            boolean ruleReserveOverflow) { }
}
