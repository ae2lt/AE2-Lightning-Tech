package com.moakiee.ae2lt.network;

import com.moakiee.ae2lt.blockentity.TianshuSupercomputerControllerBlockEntity;
import com.moakiee.ae2lt.blockentity.TianshuSupercomputerPortBlockEntity;
import com.moakiee.ae2lt.menu.TianshuSupercomputerControllerMenu;
import com.moakiee.thunderbolt.core.crafting.algorithm.menu.CraftingAlgorithmProviderMenu;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TianshuControllerActionPacket(int token, BlockPos pos, Action action) implements CustomPacketPayload {
    public static final Type<TianshuControllerActionPacket> TYPE =
            new Type<>(NetworkInit.id("tianshu_controller_action"));
    public static final StreamCodec<FriendlyByteBuf, TianshuControllerActionPacket> STREAM_CODEC =
            StreamCodec.of(TianshuControllerActionPacket::encode, TianshuControllerActionPacket::decode);

    private static void encode(FriendlyByteBuf buf, TianshuControllerActionPacket packet) {
        buf.writeVarInt(packet.token);
        buf.writeBlockPos(packet.pos);
        buf.writeEnum(packet.action);
    }

    private static TianshuControllerActionPacket decode(FriendlyByteBuf buf) {
        return new TianshuControllerActionPacket(buf.readVarInt(), buf.readBlockPos(), buf.readEnum(Action.class));
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
        switch (action) {
            case AUTO_BUILD -> controller.autoBuild(player);
            case OPEN_ALGORITHM_SELECTION -> {
                var portPos = controller.getPortPos();
                if (portPos != null
                        && player.level().getBlockEntity(portPos)
                                instanceof TianshuSupercomputerPortBlockEntity port
                        && port.getController() == controller) {
                    MenuOpener.open(
                            CraftingAlgorithmProviderMenu.TYPE,
                            player,
                            MenuLocators.forBlockEntity(port));
                } else {
                    player.displayClientMessage(Component.translatable("ae2lt.gui.error.rejected")
                            .withStyle(ChatFormatting.RED), true);
                }
            }
        }
    }

    public enum Action {
        AUTO_BUILD,
        OPEN_ALGORITHM_SELECTION
    }
}
