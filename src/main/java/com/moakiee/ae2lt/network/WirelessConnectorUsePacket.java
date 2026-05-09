package com.moakiee.ae2lt.network;

import com.moakiee.ae2lt.block.OverloadedInterfaceBlock;
import com.moakiee.ae2lt.block.OverloadedPatternProviderBlock;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.item.OverloadedWirelessConnectorItem;
import com.moakiee.ae2lt.logic.WirelessConnectorTargetHelper;
import java.util.ArrayList;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record WirelessConnectorUsePacket(
        InteractionHand hand,
        BlockPos pos,
        Direction face,
        boolean contiguous
) implements CustomPacketPayload {
    public static final Type<WirelessConnectorUsePacket> TYPE =
            new Type<>(NetworkInit.id("wireless_connector_use"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WirelessConnectorUsePacket> STREAM_CODEC =
            StreamCodec.ofMember(WirelessConnectorUsePacket::write, WirelessConnectorUsePacket::decode);

    @Override
    public Type<WirelessConnectorUsePacket> type() {
        return TYPE;
    }

    public static WirelessConnectorUsePacket decode(RegistryFriendlyByteBuf buf) {
        return new WirelessConnectorUsePacket(
                buf.readEnum(InteractionHand.class),
                buf.readBlockPos(),
                buf.readEnum(Direction.class),
                buf.readBoolean());
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeEnum(hand);
        buf.writeBlockPos(pos);
        buf.writeEnum(face);
        buf.writeBoolean(contiguous);
    }

    public static void handle(WirelessConnectorUsePacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                payload.handleOnServer(player);
            }
        });
    }

    private void handleOnServer(ServerPlayer player) {
        var level = player.level();
        if (!level.isLoaded(pos)) {
            return;
        }

        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof OverloadedWirelessConnectorItem)) {
            return;
        }
        if (!level.mayInteract(player, pos)
                || !player.mayUseItemAt(pos, face, stack)
                || player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > 64.0D) {
            return;
        }

        var state = level.getBlockState(pos);
        var targetBe = level.getBlockEntity(pos);
        boolean isProvider = state.getBlock() instanceof OverloadedPatternProviderBlock;
        boolean isInterface = state.getBlock() instanceof OverloadedInterfaceBlock;
        boolean isHost = isProvider || isInterface;
        boolean isMachine = targetBe != null;

        if (!isHost && !isMachine) {
            return;
        }

        if (isProvider) {
            if (!(targetBe instanceof OverloadedPatternProviderBlockEntity provider)
                    || provider.getProviderMode() != OverloadedPatternProviderBlockEntity.ProviderMode.WIRELESS) {
                sendAction(player, "ae2lt.connector.need_wireless", ChatFormatting.GREEN);
                return;
            }
            OverloadedWirelessConnectorItem.selectHost(
                    stack,
                    level,
                    pos,
                    OverloadedWirelessConnectorItem.HOST_PROVIDER);
            sendAction(player, Component.translatable(
                    "ae2lt.connector.selected", pos.getX(), pos.getY(), pos.getZ())
                    .withStyle(ChatFormatting.GREEN));
            return;
        }

        if (isInterface) {
            if (!(targetBe instanceof OverloadedInterfaceBlockEntity iface)
                    || iface.getInterfaceMode() != OverloadedInterfaceBlockEntity.InterfaceMode.WIRELESS) {
                sendAction(player, "ae2lt.connector.need_wireless", ChatFormatting.GREEN);
                return;
            }
            OverloadedWirelessConnectorItem.selectHost(
                    stack,
                    level,
                    pos,
                    OverloadedWirelessConnectorItem.HOST_INTERFACE);
            sendAction(player, Component.translatable(
                    "ae2lt.connector.selected_interface", pos.getX(), pos.getY(), pos.getZ())
                    .withStyle(ChatFormatting.GREEN));
            return;
        }

        if (!OverloadedWirelessConnectorItem.hasSelection(stack)) {
            return;
        }

        var hostType = OverloadedWirelessConnectorItem.getSelectedHostType(stack);
        if (!OverloadedWirelessConnectorItem.isSelectionInCurrentDimension(level, stack)) {
            sendAction(player, "ae2lt.connector.dimension_mismatch", ChatFormatting.RED);
            return;
        }

        if (OverloadedWirelessConnectorItem.HOST_PROVIDER.equals(hostType)) {
            handleProviderConnection(player, level, stack);
        } else if (OverloadedWirelessConnectorItem.HOST_INTERFACE.equals(hostType)) {
            handleInterfaceConnection(player, level, stack);
        } else {
            OverloadedWirelessConnectorItem.clearSelection(stack);
        }
    }

    private void handleProviderConnection(ServerPlayer player, Level level, ItemStack stack) {
        var provider = OverloadedWirelessConnectorItem.getSelectedProvider(level, stack);
        if (provider == null) {
            sendAction(player, "ae2lt.connector.provider_lost", ChatFormatting.GREEN);
            OverloadedWirelessConnectorItem.clearSelection(stack);
            return;
        }

        if (level.getBlockEntity(pos) instanceof OverloadedPatternProviderBlockEntity) {
            sendAction(player, "ae2lt.connector.cannot_bind_provider", ChatFormatting.RED);
            return;
        }

        var targets = WirelessConnectorTargetHelper.collectTargets(level, pos, contiguous);
        if (targets.isEmpty()) {
            sendAction(player, "ae2lt.connector.not_machine", ChatFormatting.GREEN);
            return;
        }

        var targetDim = level.dimension();
        var disconnected = new ArrayList<BlockPos>();
        var updated = new ArrayList<BlockPos>();
        var connected = new ArrayList<BlockPos>();
        int skippedDueToLimit = 0;

        for (var targetPos : targets) {
            var existing = provider.getConnections().stream()
                    .filter(c -> c.sameTarget(targetDim, targetPos))
                    .findFirst()
                    .orElse(null);

            if (existing != null) {
                if (existing.boundFace() == face) {
                    if (provider.removeConnection(targetDim, targetPos)) {
                        disconnected.add(targetPos.immutable());
                    }
                } else if (provider.addOrUpdateConnection(targetDim, targetPos, face)) {
                    updated.add(targetPos.immutable());
                }
            } else if (provider.addOrUpdateConnection(targetDim, targetPos, face)) {
                connected.add(targetPos.immutable());
            } else {
                skippedDueToLimit++;
            }
        }

        sendProviderConnectionFeedback(player, disconnected, updated, connected, skippedDueToLimit);
    }

    private void sendProviderConnectionFeedback(
            ServerPlayer player,
            ArrayList<BlockPos> disconnected,
            ArrayList<BlockPos> updated,
            ArrayList<BlockPos> connected,
            int skippedDueToLimit) {
        if (skippedDueToLimit > 0) {
            int changed = disconnected.size() + updated.size() + connected.size();
            if (changed > 0) {
                sendAction(player, Component.translatable(
                        "ae2lt.connector.provider_partial",
                        changed,
                        skippedDueToLimit,
                        OverloadedPatternProviderBlockEntity.MAX_WIRELESS_CONNECTIONS)
                        .withStyle(ChatFormatting.GREEN));
            } else {
                sendAction(player, Component.translatable(
                        "ae2lt.connector.provider_full",
                        skippedDueToLimit,
                        OverloadedPatternProviderBlockEntity.MAX_WIRELESS_CONNECTIONS)
                        .withStyle(ChatFormatting.RED));
            }
            return;
        }

        sendConnectionFeedback(player, disconnected, updated, connected);
    }

    private void handleInterfaceConnection(ServerPlayer player, Level level, ItemStack stack) {
        var iface = OverloadedWirelessConnectorItem.getSelectedInterface(level, stack);
        if (iface == null) {
            sendAction(player, "ae2lt.connector.provider_lost", ChatFormatting.GREEN);
            OverloadedWirelessConnectorItem.clearSelection(stack);
            return;
        }

        if (level.getBlockEntity(pos) instanceof OverloadedInterfaceBlockEntity) {
            sendAction(player, "ae2lt.connector.cannot_bind_provider", ChatFormatting.RED);
            return;
        }

        var targets = WirelessConnectorTargetHelper.collectTargets(level, pos, contiguous);
        if (targets.isEmpty()) {
            sendAction(player, "ae2lt.connector.not_machine", ChatFormatting.GREEN);
            return;
        }

        var targetDim = level.dimension();
        var disconnected = new ArrayList<BlockPos>();
        var updated = new ArrayList<BlockPos>();
        var connected = new ArrayList<BlockPos>();

        for (var targetPos : targets) {
            var existing = iface.getConnections().stream()
                    .filter(c -> c.dimension().equals(targetDim) && c.pos().equals(targetPos))
                    .findFirst()
                    .orElse(null);

            if (existing != null) {
                if (existing.boundFace() == face) {
                    iface.removeConnection(targetDim, targetPos);
                    disconnected.add(targetPos.immutable());
                } else {
                    iface.addOrUpdateConnection(
                            new OverloadedInterfaceBlockEntity.WirelessConnection(targetDim, targetPos, face));
                    updated.add(targetPos.immutable());
                }
            } else {
                iface.addOrUpdateConnection(
                        new OverloadedInterfaceBlockEntity.WirelessConnection(targetDim, targetPos, face));
                connected.add(targetPos.immutable());
            }
        }

        sendConnectionFeedback(player, disconnected, updated, connected);
    }

    private void sendConnectionFeedback(
            ServerPlayer player,
            ArrayList<BlockPos> disconnected,
            ArrayList<BlockPos> updated,
            ArrayList<BlockPos> connected) {
        boolean many = disconnected.size() + updated.size() + connected.size() > 1;
        var faceName = face.getSerializedName();

        if (many) {
            if (!disconnected.isEmpty()) {
                sendAction(player, Component.translatable(
                        "ae2lt.connector.disconnected_many", disconnected.size(), faceName)
                        .withStyle(ChatFormatting.GREEN));
            } else if (!updated.isEmpty()) {
                sendAction(player, Component.translatable(
                        "ae2lt.connector.updated_many", updated.size(), faceName)
                        .withStyle(ChatFormatting.GREEN));
            } else if (!connected.isEmpty()) {
                sendAction(player, Component.translatable(
                        "ae2lt.connector.connected_many", connected.size(), faceName)
                        .withStyle(ChatFormatting.GREEN));
            }
            return;
        }

        if (!disconnected.isEmpty()) {
            var p = disconnected.get(0);
            sendAction(player, Component.translatable(
                    "ae2lt.connector.disconnected", p.getX(), p.getY(), p.getZ())
                    .withStyle(ChatFormatting.GREEN));
        } else if (!updated.isEmpty()) {
            var p = updated.get(0);
            sendAction(player, Component.translatable(
                    "ae2lt.connector.updated", p.getX(), p.getY(), p.getZ(), faceName)
                    .withStyle(ChatFormatting.GREEN));
        } else if (!connected.isEmpty()) {
            var p = connected.get(0);
            sendAction(player, Component.translatable(
                    "ae2lt.connector.connected", p.getX(), p.getY(), p.getZ(), faceName)
                    .withStyle(ChatFormatting.GREEN));
        }
    }

    private static void sendAction(ServerPlayer player, String translationKey, ChatFormatting formatting) {
        sendAction(player, Component.translatable(translationKey).withStyle(formatting));
    }

    private static void sendAction(ServerPlayer player, Component component) {
        player.sendSystemMessage(component, true);
    }
}
