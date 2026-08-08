package com.moakiee.ae2lt.network.tianshu;
import java.util.function.Supplier;
import appeng.api.stacks.AEKey;
import com.moakiee.ae2lt.logic.tianshu.maintenance.ReservedStockMatchMode;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import com.moakiee.ae2lt.network.NetworkInit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraftforge.network.NetworkEvent;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public record SaveMaintenanceRulePacket(
        int containerId, int selectionRevision, AEKey target,
        UUID expectedRuleId, boolean delete,
        long lower, long upper, long amountPerJob, boolean enabled,
        List<ReserveEdit> reserves) {
public SaveMaintenanceRulePacket {
        reserves = List.copyOf(reserves);
        TianshuPacketLimits.requireListSize("maintenance reserve edits", reserves.size());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeVarInt(selectionRevision);
        AEKey.writeKey(buf, target);
        buf.writeBoolean(expectedRuleId != null);
        if (expectedRuleId != null) buf.writeUUID(expectedRuleId);
        buf.writeBoolean(delete);
        buf.writeVarLong(lower);
        buf.writeVarLong(upper);
        buf.writeVarLong(amountPerJob);
        buf.writeBoolean(enabled);
        buf.writeVarInt(reserves.size());
        for (var edit : reserves) {
            AEKey.writeKey(buf, edit.key());
            buf.writeLong(edit.globalAmount());
            buf.writeEnum(edit.globalMode());
            buf.writeLong(edit.ruleAmount());
            buf.writeEnum(edit.ruleMode());
        }
    }

    public static SaveMaintenanceRulePacket decode(FriendlyByteBuf buf) {
        int container = buf.readVarInt();
        int selectionRevision = buf.readVarInt();
        AEKey target = AEKey.readKey(buf);
        UUID id = buf.readBoolean() ? buf.readUUID() : null;
        boolean delete = buf.readBoolean();
        long lower = buf.readVarLong();
        long upper = buf.readVarLong();
        long batch = buf.readVarLong();
        boolean enabled = buf.readBoolean();
        int size = TianshuPacketLimits.requireDecodedListSize(
                "maintenance reserve edits", buf.readVarInt());
        var edits = new ArrayList<ReserveEdit>(size);
        for (int i = 0; i < size; i++) {
            edits.add(new ReserveEdit(AEKey.readKey(buf),
                    buf.readLong(), buf.readEnum(ReservedStockMatchMode.class),
                    buf.readLong(), buf.readEnum(ReservedStockMatchMode.class)));
        }
        return new SaveMaintenanceRulePacket(container, selectionRevision, target, id, delete,
                lower, upper, batch, enabled, edits);
    }
public static void handle(SaveMaintenanceRulePacket packet, Supplier<NetworkEvent.Context> context) {
        var ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
        if (player != null && player.containerMenu instanceof TianshuPatternEncodingTermMenu menu
                    && menu.containerId == packet.containerId()) {
                menu.saveMaintenanceRule(packet);
            }
        });
        ctx.setPacketHandled(true);
    }

    public record ReserveEdit(AEKey key, long globalAmount, ReservedStockMatchMode globalMode,
                              long ruleAmount, ReservedStockMatchMode ruleMode) { }
}
