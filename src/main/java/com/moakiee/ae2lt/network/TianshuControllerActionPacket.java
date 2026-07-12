package com.moakiee.ae2lt.network;

import com.moakiee.ae2lt.blockentity.TianshuSupercomputerControllerBlockEntity;
import com.moakiee.ae2lt.menu.TianshuSupercomputerControllerMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TianshuControllerActionPacket(int token, BlockPos pos) implements CustomPacketPayload {
    public static final Type<TianshuControllerActionPacket> TYPE =
            new Type<>(NetworkInit.id("tianshu_controller_action"));
    public static final StreamCodec<FriendlyByteBuf, TianshuControllerActionPacket> STREAM_CODEC =
            StreamCodec.of(TianshuControllerActionPacket::encode, TianshuControllerActionPacket::decode);

    private static void encode(FriendlyByteBuf buf, TianshuControllerActionPacket packet) {
        buf.writeVarInt(packet.token);
        buf.writeBlockPos(packet.pos);
    }

    private static TianshuControllerActionPacket decode(FriendlyByteBuf buf) {
        return new TianshuControllerActionPacket(buf.readVarInt(), buf.readBlockPos());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TianshuControllerActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                packet.handleOnServer(player);
            }
        });
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
        controller.autoBuild(player);
    }
}
