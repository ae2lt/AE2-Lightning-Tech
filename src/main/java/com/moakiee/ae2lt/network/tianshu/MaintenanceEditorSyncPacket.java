package com.moakiee.ae2lt.network.tianshu;

import appeng.api.stacks.AEKey;
import com.moakiee.ae2lt.logic.tianshu.maintenance.InventoryMaintenanceStatus;
import com.moakiee.ae2lt.logic.tianshu.maintenance.ReservedStockMatchMode;
import com.moakiee.ae2lt.logic.tianshu.terminal.MaintenanceEditorData;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import com.moakiee.ae2lt.network.NetworkInit;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record MaintenanceEditorSyncPacket(
        int containerId, int selectionRevision, MaintenanceEditorData data)
        implements CustomPacketPayload {
    public static final Type<MaintenanceEditorSyncPacket> TYPE =
            new Type<>(NetworkInit.id("maintenance_editor_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MaintenanceEditorSyncPacket> STREAM_CODEC =
            StreamCodec.ofMember(MaintenanceEditorSyncPacket::write, MaintenanceEditorSyncPacket::decode);

    public MaintenanceEditorSyncPacket {
        TianshuPacketLimits.requireListSize("maintenance topology", data.topology().size());
        TianshuPacketLimits.requireListSize("maintenance variants", data.variants().size());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeVarInt(selectionRevision);
        AEKey.STREAM_CODEC.encode(buf, data.target());
        buf.writeBoolean(data.ruleId() != null);
        if (data.ruleId() != null) buf.writeUUID(data.ruleId());
        buf.writeVarLong(data.lowerThreshold());
        buf.writeVarLong(data.upperThreshold());
        buf.writeVarLong(data.amountPerJob());
        buf.writeBoolean(data.enabled());
        buf.writeEnum(data.status());
        buf.writeVarLong(data.currentStock());
        buf.writeBoolean(data.craftable());
        buf.writeBoolean(data.recoveryPage());
        buf.writeVarInt(data.topology().size());
        for (var entry : data.topology()) {
            AEKey.STREAM_CODEC.encode(buf, entry.key());
            buf.writeVarInt(entry.depth());
            buf.writeBoolean(entry.craftable());
            buf.writeVarLong(entry.storedAmount());
            buf.writeLong(entry.globalReserve());
            buf.writeEnum(entry.globalMode());
            buf.writeLong(entry.ruleReserve());
            buf.writeEnum(entry.ruleMode());
        }
        buf.writeVarInt(data.variants().size());
        for (var entry : data.variants()) {
            AEKey.STREAM_CODEC.encode(buf, entry.key());
            buf.writeVarLong(entry.storedAmount());
            buf.writeBoolean(entry.craftable());
        }
    }

    private static MaintenanceEditorSyncPacket decode(RegistryFriendlyByteBuf buf) {
        int container = buf.readVarInt();
        int selectionRevision = buf.readVarInt();
        AEKey target = AEKey.STREAM_CODEC.decode(buf);
        UUID ruleId = buf.readBoolean() ? buf.readUUID() : null;
        long lower = buf.readVarLong();
        long upper = buf.readVarLong();
        long batch = buf.readVarLong();
        boolean enabled = buf.readBoolean();
        var status = buf.readEnum(InventoryMaintenanceStatus.class);
        long currentStock = buf.readVarLong();
        boolean craftable = buf.readBoolean();
        boolean recoveryPage = buf.readBoolean();
        int topologySize = TianshuPacketLimits.requireDecodedListSize(
                "maintenance topology", buf.readVarInt());
        var topology = new ArrayList<MaintenanceEditorData.TopologyEntry>(topologySize);
        for (int i = 0; i < topologySize; i++) {
            topology.add(new MaintenanceEditorData.TopologyEntry(
                    AEKey.STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readBoolean(),
                    buf.readVarLong(),
                    buf.readLong(), buf.readEnum(ReservedStockMatchMode.class),
                    buf.readLong(), buf.readEnum(ReservedStockMatchMode.class)));
        }
        int variantSize = TianshuPacketLimits.requireDecodedListSize(
                "maintenance variants", buf.readVarInt());
        var variants = new ArrayList<MaintenanceEditorData.VariantEntry>(variantSize);
        for (int i = 0; i < variantSize; i++) {
            variants.add(new MaintenanceEditorData.VariantEntry(
                    AEKey.STREAM_CODEC.decode(buf), buf.readVarLong(), buf.readBoolean()));
        }
        return new MaintenanceEditorSyncPacket(container, selectionRevision, new MaintenanceEditorData(
                target, ruleId, lower, upper, batch, enabled, status,
                currentStock, craftable, recoveryPage, topology, variants));
    }

    @Override public Type<MaintenanceEditorSyncPacket> type() { return TYPE; }

    public static void handle(MaintenanceEditorSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof TianshuPatternEncodingTermMenu menu
                    && menu.containerId == packet.containerId()) {
                menu.receiveMaintenanceEditorData(packet.selectionRevision(), packet.data());
            }
        });
    }
}
