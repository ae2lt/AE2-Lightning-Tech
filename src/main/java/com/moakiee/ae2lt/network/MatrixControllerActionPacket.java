package com.moakiee.ae2lt.network;

import com.moakiee.ae2lt.blockentity.MatrixControllerBlockEntity;
import com.moakiee.ae2lt.menu.MatrixControllerMenu;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record MatrixControllerActionPacket(int token, BlockPos pos, Action action) implements CustomPacketPayload {
    public static final Type<MatrixControllerActionPacket> TYPE =
            new Type<>(NetworkInit.id("matrix_controller_action"));
    public static final StreamCodec<FriendlyByteBuf, MatrixControllerActionPacket> STREAM_CODEC =
            StreamCodec.of(MatrixControllerActionPacket::encode, MatrixControllerActionPacket::decode);

    public enum Action {
        SCAN_FORM,
        AUTO_BUILD,
        UPGRADE_PATTERN_STORAGE,
        DEFORM
    }

    private static void encode(FriendlyByteBuf buf, MatrixControllerActionPacket packet) {
        buf.writeVarInt(packet.token);
        buf.writeBlockPos(packet.pos);
        buf.writeEnum(packet.action);
    }

    private static MatrixControllerActionPacket decode(FriendlyByteBuf buf) {
        return new MatrixControllerActionPacket(buf.readVarInt(), buf.readBlockPos(), buf.readEnum(Action.class));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MatrixControllerActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                packet.handleOnServer(player);
            }
        });
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
