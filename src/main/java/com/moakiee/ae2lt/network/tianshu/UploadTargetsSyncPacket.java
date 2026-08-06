package com.moakiee.ae2lt.network.tianshu;

import appeng.api.implementations.blockentities.PatternContainerGroup;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuUploadTargetData;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import com.moakiee.ae2lt.network.NetworkInit;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record UploadTargetsSyncPacket(int containerId, List<TianshuUploadTargetData> targets)
        implements CustomPacketPayload {
    public static final Type<UploadTargetsSyncPacket> TYPE =
            new Type<>(NetworkInit.id("upload_targets_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UploadTargetsSyncPacket> STREAM_CODEC =
            StreamCodec.ofMember(UploadTargetsSyncPacket::write, UploadTargetsSyncPacket::decode);

    public UploadTargetsSyncPacket {
        targets = targets == null ? List.of() : List.copyOf(targets);
        TianshuPacketLimits.requireListSize("upload targets", targets.size());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeVarInt(targets.size());
        for (var target : targets) {
            target.group().writeToPacket(buf);
            buf.writeVarInt(target.providerCount());
            buf.writeVarInt(target.availableSlots());
        }
    }

    private static UploadTargetsSyncPacket decode(RegistryFriendlyByteBuf buf) {
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

    @Override public Type<UploadTargetsSyncPacket> type() { return TYPE; }

    public static void handle(UploadTargetsSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof TianshuPatternEncodingTermMenu menu
                    && menu.containerId == packet.containerId()) {
                menu.receiveUploadTargets(packet.targets());
            }
        });
    }
}
