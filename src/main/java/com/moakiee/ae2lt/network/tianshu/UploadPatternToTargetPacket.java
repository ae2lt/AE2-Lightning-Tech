package com.moakiee.ae2lt.network.tianshu;
import java.util.function.Supplier;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import com.moakiee.ae2lt.network.NetworkInit;
import net.minecraftforge.network.NetworkEvent;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraft.server.level.ServerPlayer;

public record UploadPatternToTargetPacket(int containerId, PatternContainerGroup group) {
public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        group.writeToPacket(buf);
    }

    public static UploadPatternToTargetPacket decode(FriendlyByteBuf buf) {
        return new UploadPatternToTargetPacket(
                buf.readVarInt(), PatternContainerGroup.readFromPacket(buf));
    }
public static void handle(UploadPatternToTargetPacket packet, Supplier<NetworkEvent.Context> context) {
        var ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
        if (player != null
                    && player.containerMenu instanceof TianshuPatternEncodingTermMenu menu
                    && menu.containerId == packet.containerId()) {
                menu.uploadTianshuPatternToTarget(player, packet.group());
            }
        });
        ctx.setPacketHandled(true);
    }
}
