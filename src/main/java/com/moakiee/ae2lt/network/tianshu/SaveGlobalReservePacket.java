package com.moakiee.ae2lt.network.tianshu;
import java.util.function.Supplier;
import appeng.api.stacks.AEKey;
import com.moakiee.ae2lt.logic.tianshu.maintenance.ReservedStockMatchMode;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import net.minecraftforge.network.NetworkEvent;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public record SaveGlobalReservePacket(
        int containerId, int selectionRevision, AEKey key,
        long amount, ReservedStockMatchMode mode) {
public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeVarInt(selectionRevision);
        AEKey.writeKey(buf, key);
        buf.writeLong(amount);
        buf.writeEnum(mode);
    }

    public static SaveGlobalReservePacket decode(FriendlyByteBuf buf) {
        return new SaveGlobalReservePacket(buf.readVarInt(), buf.readVarInt(),
                AEKey.readKey(buf),
                buf.readLong(), buf.readEnum(ReservedStockMatchMode.class));
    }
public static void handle(SaveGlobalReservePacket packet, Supplier<NetworkEvent.Context> context) {
        var ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
        if (player != null && player.containerMenu instanceof TianshuPatternEncodingTermMenu menu
                    && menu.containerId == packet.containerId()) menu.saveGlobalReserve(packet);
        });
        ctx.setPacketHandled(true);
    }
}
