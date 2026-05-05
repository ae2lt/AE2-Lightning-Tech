package com.moakiee.ae2lt.network;

import com.moakiee.ae2lt.block.OverloadedInterfaceBlock;
import com.moakiee.ae2lt.block.OverloadedPatternProviderBlock;
import com.moakiee.ae2lt.block.OverloadedPowerSupplyBlock;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPowerSupplyBlockEntity;
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
        if (!level.isLoaded(pos)) return;

        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof OverloadedWirelessConnectorItem)) return;
        if (!player.canInteractWithBlock(pos, 1.0D)) return;

        var state = level.getBlockState(pos);
        var targetBe = level.getBlockEntity(pos);
        boolean isProvider = state.getBlock() instanceof OverloadedPatternProviderBlock;
        boolean isInterface = state.getBlock() instanceof OverloadedInterfaceBlock;
        boolean isPowerSupply = state.getBlock() instanceof OverloadedPowerSupplyBlock;
        boolean isHost = isProvider || isInterface || isPowerSupply;
        boolean isMachine = targetBe != null;

        if (!isHost && !isMachine) return;

        // ── Clicking a host block: select it ─────────────────────────────
        if (isProvider) {
            if (targetBe instanceof OverloadedPatternProviderBlockEntity provider
                    && provider.getProviderMode() == OverloadedPatternProviderBlockEntity.ProviderMode.NORMAL) {
                player.displayClientMessage(
                        Component.translatable("ae2lt.connector.need_wireless").withStyle(ChatFormatting.GREEN), true);
                return;
            }
            OverloadedWirelessConnectorItem.selectHost(stack, level, pos,
                    OverloadedWirelessConnectorItem.HOST_PROVIDER);
            player.displayClientMessage(
                    Component.translatable("ae2lt.connector.selected", pos.getX(), pos.getY(), pos.getZ())
                            .withStyle(ChatFormatting.GREEN), true);
            return;
        }

        if (isInterface) {
            if (targetBe instanceof OverloadedInterfaceBlockEntity iface
                    && iface.getInterfaceMode() != OverloadedInterfaceBlockEntity.InterfaceMode.WIRELESS) {
                player.displayClientMessage(
                        Component.translatable("ae2lt.connector.need_wireless").withStyle(ChatFormatting.GREEN), true);
                return;
            }
            OverloadedWirelessConnectorItem.selectHost(stack, level, pos,
                    OverloadedWirelessConnectorItem.HOST_INTERFACE);
            player.displayClientMessage(
                    Component.translatable("ae2lt.connector.selected_interface",
                            pos.getX(), pos.getY(), pos.getZ())
                            .withStyle(ChatFormatting.GREEN), true);
            return;
        }

        if (isPowerSupply) {
            OverloadedWirelessConnectorItem.selectHost(stack, level, pos,
                    OverloadedWirelessConnectorItem.HOST_POWER_SUPPLY);
            player.displayClientMessage(
                    Component.translatable("ae2lt.connector.selected_power_supply",
                            pos.getX(), pos.getY(), pos.getZ())
                            .withStyle(ChatFormatting.GREEN), true);
            return;
        }

        // ── Clicking a machine: connect to the selected host ─────────────
        if (!OverloadedWirelessConnectorItem.hasSelection(stack)) {
            return;
        }

        var hostType = OverloadedWirelessConnectorItem.getSelectedHostType(stack);
        if (!OverloadedWirelessConnectorItem.isSelectionInCurrentDimension(level, stack)) {
            player.displayClientMessage(
                    Component.translatable("ae2lt.connector.dimension_mismatch")
                            .withStyle(ChatFormatting.RED), true);
            return;
        }

        if (OverloadedWirelessConnectorItem.HOST_PROVIDER.equals(hostType)) {
            handleProviderConnection(player, level, stack);
        } else if (OverloadedWirelessConnectorItem.HOST_INTERFACE.equals(hostType)) {
            handleInterfaceConnection(player, level, stack);
        } else if (OverloadedWirelessConnectorItem.HOST_POWER_SUPPLY.equals(hostType)) {
            handlePowerSupplyConnection(player, level, stack);
        }
    }

    private void handleProviderConnection(ServerPlayer player, net.minecraft.world.level.Level level, ItemStack stack) {
        var provider = OverloadedWirelessConnectorItem.getSelectedProvider(level, stack);
        if (provider == null) {
            player.displayClientMessage(
                    Component.translatable("ae2lt.connector.provider_lost").withStyle(ChatFormatting.GREEN), true);
            OverloadedWirelessConnectorItem.clearSelection(stack);
            return;
        }

        if (level.getBlockEntity(pos) instanceof OverloadedPatternProviderBlockEntity) {
            player.displayClientMessage(
                    Component.translatable("ae2lt.connector.cannot_bind_provider")
                            .withStyle(ChatFormatting.RED), true);
            return;
        }

        var targets = WirelessConnectorTargetHelper.collectTargets(level, pos, contiguous);
        if (targets.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("ae2lt.connector.not_machine").withStyle(ChatFormatting.GREEN), true);
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
                    .findFirst().orElse(null);

            if (existing != null) {
                if (existing.boundFace() == face) {
                    if (provider.removeConnection(targetDim, targetPos)) {
                        disconnected.add(targetPos.immutable());
                    }
                } else {
                    if (provider.addOrUpdateConnection(targetDim, targetPos, face)) {
                        updated.add(targetPos.immutable());
                    }
                }
            } else {
                if (provider.addOrUpdateConnection(targetDim, targetPos, face)) {
                    connected.add(targetPos.immutable());
                } else {
                    skippedDueToLimit++;
                }
            }
        }

        sendProviderConnectionFeedback(player, disconnected, updated, connected, skippedDueToLimit);
    }

    private void sendProviderConnectionFeedback(ServerPlayer player,
                                                ArrayList<BlockPos> disconnected,
                                                ArrayList<BlockPos> updated,
                                                ArrayList<BlockPos> connected,
                                                int skippedDueToLimit) {
        if (skippedDueToLimit > 0) {
            int changed = disconnected.size() + updated.size() + connected.size();
            if (changed > 0) {
                player.displayClientMessage(Component.translatable(
                        "ae2lt.connector.provider_partial",
                        changed,
                        skippedDueToLimit,
                        OverloadedPatternProviderBlockEntity.MAX_WIRELESS_CONNECTIONS)
                        .withStyle(ChatFormatting.GREEN), true);
                return;
            }
            player.displayClientMessage(Component.translatable(
                    "ae2lt.connector.provider_full",
                    skippedDueToLimit,
                    OverloadedPatternProviderBlockEntity.MAX_WIRELESS_CONNECTIONS)
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        sendConnectionFeedback(player, disconnected, updated, connected);
    }

    private void handleInterfaceConnection(ServerPlayer player, net.minecraft.world.level.Level level, ItemStack stack) {
        var iface = OverloadedWirelessConnectorItem.getSelectedInterface(level, stack);
        if (iface == null) {
            player.displayClientMessage(
                    Component.translatable("ae2lt.connector.provider_lost").withStyle(ChatFormatting.GREEN), true);
            OverloadedWirelessConnectorItem.clearSelection(stack);
            return;
        }

        if (level.getBlockEntity(pos) instanceof OverloadedInterfaceBlockEntity) {
            player.displayClientMessage(
                    Component.translatable("ae2lt.connector.cannot_bind_provider")
                            .withStyle(ChatFormatting.RED), true);
            return;
        }

        var targets = WirelessConnectorTargetHelper.collectTargets(level, pos, contiguous);
        if (targets.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("ae2lt.connector.not_machine").withStyle(ChatFormatting.GREEN), true);
            return;
        }

        var targetDim = level.dimension();
        var disconnected = new ArrayList<BlockPos>();
        var updated = new ArrayList<BlockPos>();
        var connected = new ArrayList<BlockPos>();

        for (var targetPos : targets) {
            var existing = iface.getConnections().stream()
                    .filter(c -> c.dimension().equals(targetDim) && c.pos().equals(targetPos))
                    .findFirst().orElse(null);

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

    private void handlePowerSupplyConnection(ServerPlayer player, net.minecraft.world.level.Level level, ItemStack stack) {
        var powerSupply = OverloadedWirelessConnectorItem.getSelectedPowerSupply(level, stack);
        if (powerSupply == null) {
            player.displayClientMessage(
                    Component.translatable("ae2lt.connector.power_supply_lost").withStyle(ChatFormatting.GREEN), true);
            OverloadedWirelessConnectorItem.clearSelection(stack);
            return;
        }

        if (level.getBlockEntity(pos) instanceof OverloadedPowerSupplyBlockEntity) {
            player.displayClientMessage(
                    Component.translatable("ae2lt.connector.cannot_bind_power_supply")
                            .withStyle(ChatFormatting.RED), true);
            return;
        }

        var targets = WirelessConnectorTargetHelper.collectTargets(level, pos, contiguous);
        if (targets.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("ae2lt.connector.not_machine").withStyle(ChatFormatting.GREEN), true);
            return;
        }

        var targetDim = level.dimension();
        var result = powerSupply.editConnections(targetDim, targets, face);
        sendConnectionFeedback(player,
                new ArrayList<>(result.disconnected()),
                new ArrayList<>(result.updated()),
                new ArrayList<>(result.connected()),
                result.skippedDueToLimit());
    }

    private void sendConnectionFeedback(ServerPlayer player,
                                         ArrayList<BlockPos> disconnected,
                                         ArrayList<BlockPos> updated,
                                         ArrayList<BlockPos> connected) {
        sendConnectionFeedback(player, disconnected, updated, connected, 0);
    }

    private void sendConnectionFeedback(ServerPlayer player,
                                         ArrayList<BlockPos> disconnected,
                                         ArrayList<BlockPos> updated,
                                         ArrayList<BlockPos> connected,
                                         int skippedDueToLimit) {
        if (skippedDueToLimit > 0) {
            int changed = disconnected.size() + updated.size() + connected.size();
            if (changed > 0) {
                player.displayClientMessage(Component.translatable(
                        "ae2lt.connector.power_supply_partial",
                        changed,
                        skippedDueToLimit,
                        OverloadedPowerSupplyBlockEntity.MAX_WIRELESS_CONNECTIONS)
                        .withStyle(ChatFormatting.GREEN), true);
                return;
            }
            player.displayClientMessage(Component.translatable(
                    "ae2lt.connector.power_supply_full",
                    skippedDueToLimit,
                    OverloadedPowerSupplyBlockEntity.MAX_WIRELESS_CONNECTIONS)
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        boolean many = (disconnected.size() + updated.size() + connected.size()) > 1;

        if (many) {
            if (!disconnected.isEmpty()) {
                player.displayClientMessage(Component.translatable(
                        "ae2lt.connector.disconnected_many", disconnected.size(), face.getName())
                        .withStyle(ChatFormatting.GREEN), true);
            } else if (!updated.isEmpty()) {
                player.displayClientMessage(Component.translatable(
                        "ae2lt.connector.updated_many", updated.size(), face.getName())
                        .withStyle(ChatFormatting.GREEN), true);
            } else if (!connected.isEmpty()) {
                player.displayClientMessage(Component.translatable(
                        "ae2lt.connector.connected_many", connected.size(), face.getName())
                        .withStyle(ChatFormatting.GREEN), true);
            }
            return;
        }

        if (!disconnected.isEmpty()) {
            var p = disconnected.getFirst();
            player.displayClientMessage(Component.translatable(
                    "ae2lt.connector.disconnected", p.getX(), p.getY(), p.getZ())
                    .withStyle(ChatFormatting.GREEN), true);
        } else if (!updated.isEmpty()) {
            var p = updated.getFirst();
            player.displayClientMessage(Component.translatable(
                    "ae2lt.connector.updated", p.getX(), p.getY(), p.getZ(), face.getName())
                    .withStyle(ChatFormatting.GREEN), true);
        } else if (!connected.isEmpty()) {
            var p = connected.getFirst();
            player.displayClientMessage(Component.translatable(
                    "ae2lt.connector.connected", p.getX(), p.getY(), p.getZ(), face.getName())
                    .withStyle(ChatFormatting.GREEN), true);
        }
    }
}
