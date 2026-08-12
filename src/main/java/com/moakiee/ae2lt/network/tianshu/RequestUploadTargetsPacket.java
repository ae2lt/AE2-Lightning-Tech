package com.moakiee.ae2lt.network.tianshu;
import java.util.function.Supplier;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import net.minecraftforge.network.NetworkEvent;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraft.server.level.ServerPlayer;

public record RequestUploadTargetsPacket(int containerId) {
public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
    }

    public static RequestUploadTargetsPacket decode(FriendlyByteBuf buf) {
        return new RequestUploadTargetsPacket(buf.readVarInt());
    }
public static void handle(RequestUploadTargetsPacket packet, Supplier<NetworkEvent.Context> context) {
        var ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
        if (player != null
                    && player.containerMenu instanceof TianshuPatternEncodingTermMenu menu
                    && menu.containerId == packet.containerId()) {
                menu.sendUploadTargets(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
