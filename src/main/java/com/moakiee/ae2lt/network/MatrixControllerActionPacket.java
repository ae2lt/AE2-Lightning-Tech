package com.moakiee.ae2lt.network;

import java.util.function.Supplier;

import com.moakiee.ae2lt.blockentity.MatrixControllerBlockEntity;
import com.moakiee.ae2lt.menu.MatrixControllerMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public record MatrixControllerActionPacket(int token, BlockPos pos, Action action) {
    public enum Action {
        AUTO_BUILD,
        UPGRADE_PATTERN_STORAGE
    }

    public static void encode(MatrixControllerActionPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.token);
        buf.writeBlockPos(packet.pos);
        buf.writeEnum(packet.action);
    }

    public static MatrixControllerActionPacket decode(FriendlyByteBuf buf) {
        return new MatrixControllerActionPacket(buf.readVarInt(), buf.readBlockPos(), buf.readEnum(Action.class));
    }
    public static void handle(MatrixControllerActionPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                packet.handleOnServer(player);
            }
        });
        ctx.setPacketHandled(true);
    }

    private void handleOnServer(ServerPlayer player) {
        if (!(player.containerMenu instanceof MatrixControllerMenu menu)
                || menu.token() != token
                || !menu.getBlockPos().equals(pos)
                || !menu.stillValid(player)) {
            player.displayClientMessage(Component.translatable("ae2lt.gui.error.rejected")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        if (!(player.level().getBlockEntity(pos) instanceof MatrixControllerBlockEntity controller)) {
            player.displayClientMessage(Component.translatable("ae2lt.gui.error.rejected")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        controller.performAction(action, player);
    }
}
