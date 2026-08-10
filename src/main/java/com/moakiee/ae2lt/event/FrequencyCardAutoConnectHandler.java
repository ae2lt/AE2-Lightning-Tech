package com.moakiee.ae2lt.event;

import appeng.api.implementations.parts.ICablePart;
import appeng.api.parts.IPartItem;
import appeng.parts.PartPlacement;
import appeng.util.InteractionUtil;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.grid.wirelesslink.WirelessLinkRegistry;
import com.moakiee.ae2lt.item.OverloadedFrequencyCardItem;
import com.moakiee.ae2lt.item.TerminalCardAccess;
import com.moakiee.ae2lt.network.FrequencyCardUsePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ServerLevelAccessor;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class FrequencyCardAutoConnectHandler {
    private FrequencyCardAutoConnectHandler() {
    }

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevelAccessor levelAccessor)) {
            return;
        }
        var registry = WirelessLinkRegistry.get();
        if (registry == null) {
            return;
        }

        var level = levelAccessor.getLevel();
        if (registry.isPotentialLinkTarget(level, event.getPos())) {
            registry.queueClusterTopologyChange(level, event.getPos());
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            registry.queueAutoConnect(player, player.level().dimension(), event.getPos(), null, 2);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevelAccessor levelAccessor)) {
            return;
        }
        var registry = WirelessLinkRegistry.get();
        if (registry != null) {
            registry.onBlockChanged(levelAccessor.getLevel(), event.getPos());
        }
    }

    /**
     * AE2 cable-bus parts can be removed without breaking their host block, so a
     * BlockEvent is not guaranteed. Snapshot the linked physical cluster before
     * the click; the delayed reconciliation is harmless if the click does not
     * actually change topology.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        var registry = WirelessLinkRegistry.get();
        if (registry != null && registry.isPotentialLinkTarget(level, event.getPos())) {
            registry.prepareClusterTopologyChange(level, event.getPos());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onWrenchDisassemble(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)
                || !InteractionUtil.isInAlternateUseMode(player)
                || !InteractionUtil.canWrenchDisassemble(event.getItemStack())) {
            return;
        }
        var registry = WirelessLinkRegistry.get();
        if (registry != null && registry.isPotentialLinkTarget(level, event.getPos())) {
            registry.prepareClusterTopologyChange(level, event.getPos());
        }
    }

    /**
     * Lets a frequency card installed inside a wireless terminal link devices by
     * right-clicking them while holding the terminal — mirroring the held-card
     * behaviour. When the held item is a terminal carrying a bound card and the
     * clicked block is an AE2 network target, the interaction is intercepted
     * (so neither the device's nor the terminal's GUI opens) and the link/unlink
     * is performed server-side. Runs before {@link #onRightClickBlock}; a
     * terminal is never an {@link IPartItem}, so the two handlers never overlap.
     */
    @SubscribeEvent
    public static void onTerminalCardRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isShiftKeyDown()) {
            return;
        }
        ItemStack held = event.getItemStack();
        // The card-in-hand path is handled by the item itself.
        if (held.getItem() instanceof OverloadedFrequencyCardItem) {
            return;
        }
        ItemStack card = TerminalCardAccess.findCard(held);
        if (card.isEmpty() || !OverloadedFrequencyCardItem.getData(card).isBound()) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        var pos = event.getPos();
        var registry = WirelessLinkRegistry.get(level.getServer());
        if (registry == null || !registry.isPotentialLinkTarget(level, pos)) {
            return;
        }

        // Take over the interaction: prevent the clicked AE2 device (or the
        // terminal's own use) from opening a GUI, then run the link/unlink.
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        var face = event.getFace();
        if (face == null) {
            return;
        }
        FrequencyCardUsePacket.tryLinkWithCard(
                player, card, level, pos, face, event.getHitVec().getLocation());
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(event.getItemStack().getItem() instanceof IPartItem<?> partItem)) {
            return;
        }
        var clickedFace = event.getFace();
        if (clickedFace == null) {
            return;
        }
        var placement = PartPlacement.getPartPlacement(
                player,
                player.level(),
                event.getItemStack(),
                event.getPos(),
                clickedFace,
                event.getHitVec().getLocation());
        if (placement == null) {
            return;
        }
        var target = toAutoConnectTarget(event.getPos(), clickedFace, placement);
        var registry = WirelessLinkRegistry.get();
        if (registry != null) {
            if (player.level() instanceof ServerLevel level) {
                registry.queueClusterTopologyChange(level, target.pos());
            }
            var partId = IPartItem.getId(partItem);
            var expectedPartSideName = FrequencyCardAutoConnectTarget.placedPartStorageSideName(
                    target.side().getName(),
                    ICablePart.class.isAssignableFrom(partItem.getPartClass()));
            registry.queuePartAutoConnect(
                    player,
                    player.level().dimension(),
                    target.pos(),
                    target.side(),
                    partId.toString(),
                    expectedPartSideName,
                    2);
        }
    }

    static PartAutoConnectTarget toAutoConnectTarget(
            BlockPos clickedPos,
            Direction clickedSide,
            PartPlacement.Placement placement) {
        var target = FrequencyCardAutoConnectTarget.fromPartPlacement(
                new FrequencyCardAutoConnectTarget.GridPos(clickedPos.getX(), clickedPos.getY(), clickedPos.getZ()),
                clickedSide.getName(),
                new FrequencyCardAutoConnectTarget.GridPos(
                        placement.pos().getX(),
                        placement.pos().getY(),
                        placement.pos().getZ()),
                placement.side().getName());
        return new PartAutoConnectTarget(
                new BlockPos(target.pos().x(), target.pos().y(), target.pos().z()),
                Direction.byName(target.sideName()));
    }

    record PartAutoConnectTarget(BlockPos pos, Direction side) {
    }
}
