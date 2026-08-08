package com.moakiee.ae2lt.network.tianshu;
import java.util.function.Supplier;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuUploadTargetData;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import com.moakiee.ae2lt.network.NetworkInit;
import java.util.ArrayList;
import java.util.List;
import net.minecraftforge.network.NetworkEvent;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public record UploadTargetsSyncPacket(int containerId, List<TianshuUploadTargetData> targets) {
public UploadTargetsSyncPacket {
        targets = targets == null ? List.of() : List.copyOf(targets);
        TianshuPacketLimits.requireListSize("upload targets", targets.size());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeVarInt(targets.size());
        for (var target : targets) {
            target.group().writeToPacket(buf);
            buf.writeVarInt(target.providerCount());
            buf.writeVarInt(target.availableSlots());
        }
    }

    public static UploadTargetsSyncPacket decode(FriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        int size = TianshuPacketLimits.requireDecodedListSize(
                "upload targets", buf.readVarInt());
        var targets = new ArrayList<TianshuUploadTargetData>(size);
        for (int i = 0; i < size; i++) {
            targets.add(new TianshuUploadTargetData(
                    PatternContainerGroup.readFromPacket(buf), buf.readVarInt(), buf.readVarInt()));
        }
        return new UploadTargetsSyncPacket(containerId, targets);
    }
public static void handle(UploadTargetsSyncPacket packet, Supplier<NetworkEvent.Context> context) {
        var ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
        if (player != null && player.containerMenu instanceof TianshuPatternEncodingTermMenu menu
                    && menu.containerId == packet.containerId()) {
                menu.receiveUploadTargets(packet.targets());
            }
        });
        ctx.setPacketHandled(true);
    }
}
