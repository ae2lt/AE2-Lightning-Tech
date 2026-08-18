package com.moakiee.ae2lt.network;
import java.util.function.Supplier;
import com.moakiee.ae2lt.blockentity.TianshuSupercomputerControllerBlockEntity;
import com.moakiee.ae2lt.menu.TianshuSupercomputerControllerMenu;
import net.minecraftforge.network.NetworkEvent;
import net.minecraft.ChatFormatting;
import net.minecraftforge.network.NetworkEvent;
import net.minecraft.core.BlockPos;
import net.minecraftforge.network.NetworkEvent;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;
import net.minecraft.server.level.ServerPlayer;

public record TianshuControllerActionPacket(int token, BlockPos pos, Action action) {
    public static void encode(TianshuControllerActionPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.token);
        buf.writeBlockPos(packet.pos);
        buf.writeEnum(packet.action);
    }

    public static TianshuControllerActionPacket decode(FriendlyByteBuf buf) {
        return new TianshuControllerActionPacket(buf.readVarInt(), buf.readBlockPos(), buf.readEnum(Action.class));
    }
public static void handle(TianshuControllerActionPacket packet, Supplier<NetworkEvent.Context> context) {
        var ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
        if (player != null) {
                packet.handleOnServer(player);
            }
        });
        ctx.setPacketHandled(true);
    }

    private void handleOnServer(ServerPlayer player) {
        if (!(player.containerMenu instanceof TianshuSupercomputerControllerMenu menu)
                || menu.token() != token
                || !menu.getBlockPos().equals(pos)
                || !menu.stillValid(player)
                || !(player.level().getBlockEntity(pos) instanceof TianshuSupercomputerControllerBlockEntity controller)) {
            player.displayClientMessage(Component.translatable("ae2lt.gui.error.rejected")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        switch (action) {
            case AUTO_BUILD -> controller.autoBuild(player);
            case TOGGLE_FAST_PLANNING -> controller.toggleFastPlanning();
        }
    }

    public enum Action {
        AUTO_BUILD,
        TOGGLE_FAST_PLANNING
    }
}
