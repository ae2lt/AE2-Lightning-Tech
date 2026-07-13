package com.moakiee.ae2lt.network.tianshu;

import appeng.api.stacks.AEKey;
import com.moakiee.ae2lt.logic.tianshu.maintenance.ReservedStockMatchMode;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import com.moakiee.ae2lt.network.NetworkInit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SaveMaintenanceRulePacket(
        int containerId, AEKey target, UUID expectedRuleId, boolean delete,
        long lower, long upper, long amountPerJob, boolean enabled,
        List<ReserveEdit> reserves) implements CustomPacketPayload {
    public static final Type<SaveMaintenanceRulePacket> TYPE =
            new Type<>(NetworkInit.id("save_maintenance_rule"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SaveMaintenanceRulePacket> STREAM_CODEC =
            StreamCodec.ofMember(SaveMaintenanceRulePacket::write, SaveMaintenanceRulePacket::decode);

    public SaveMaintenanceRulePacket { reserves = List.copyOf(reserves); }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        AEKey.STREAM_CODEC.encode(buf, target);
        buf.writeBoolean(expectedRuleId != null);
        if (expectedRuleId != null) buf.writeUUID(expectedRuleId);
        buf.writeBoolean(delete);
        buf.writeVarLong(lower);
        buf.writeVarLong(upper);
        buf.writeVarLong(amountPerJob);
        buf.writeBoolean(enabled);
        buf.writeVarInt(reserves.size());
        for (var edit : reserves) {
            AEKey.STREAM_CODEC.encode(buf, edit.key());
            buf.writeLong(edit.globalAmount());
            buf.writeEnum(edit.globalMode());
            buf.writeLong(edit.ruleAmount());
            buf.writeEnum(edit.ruleMode());
        }
    }

    private static SaveMaintenanceRulePacket decode(RegistryFriendlyByteBuf buf) {
        int container = buf.readVarInt();
        AEKey target = AEKey.STREAM_CODEC.decode(buf);
        UUID id = buf.readBoolean() ? buf.readUUID() : null;
        boolean delete = buf.readBoolean();
        long lower = buf.readVarLong();
        long upper = buf.readVarLong();
        long batch = buf.readVarLong();
        boolean enabled = buf.readBoolean();
        int size = Math.min(2048, buf.readVarInt());
        var edits = new ArrayList<ReserveEdit>(size);
        for (int i = 0; i < size; i++) {
            edits.add(new ReserveEdit(AEKey.STREAM_CODEC.decode(buf),
                    buf.readLong(), buf.readEnum(ReservedStockMatchMode.class),
                    buf.readLong(), buf.readEnum(ReservedStockMatchMode.class)));
        }
        return new SaveMaintenanceRulePacket(container, target, id, delete,
                lower, upper, batch, enabled, edits);
    }

    @Override public Type<SaveMaintenanceRulePacket> type() { return TYPE; }

    public static void handle(SaveMaintenanceRulePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof TianshuPatternEncodingTermMenu menu
                    && menu.containerId == packet.containerId()) {
                menu.saveMaintenanceRule(packet);
            }
        });
    }

    public record ReserveEdit(AEKey key, long globalAmount, ReservedStockMatchMode globalMode,
                              long ruleAmount, ReservedStockMatchMode ruleMode) { }
}
