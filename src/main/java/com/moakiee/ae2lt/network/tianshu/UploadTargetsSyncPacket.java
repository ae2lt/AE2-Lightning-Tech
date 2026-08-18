package com.moakiee.ae2lt.network.tianshu;

import java.util.function.Supplier;

import appeng.api.implementations.blockentities.PatternContainerGroup;
import com.moakiee.ae2lt.client.ClientNetworkPacketHandlers;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuUploadTargetData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

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
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> ClientNetworkPacketHandlers.handleUploadTargetsSync(packet)));
        ctx.setPacketHandled(true);
    }
}
