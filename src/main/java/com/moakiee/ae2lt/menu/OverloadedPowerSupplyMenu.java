package com.moakiee.ae2lt.menu;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.slot.AppEngSlot;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.blockentity.OverloadedPowerSupplyBlockEntity;
import com.moakiee.ae2lt.logic.OverloadedPowerSupplyLogic;
import com.moakiee.ae2lt.logic.energy.AppFluxBridge;

public class OverloadedPowerSupplyMenu extends AEBaseMenu implements FrequencyBindingMenu {

    public static final MenuType<OverloadedPowerSupplyMenu> TYPE = MenuTypeBuilder
            .create(OverloadedPowerSupplyMenu::new, OverloadedPowerSupplyBlockEntity.class)
            .withMenuTitle(host -> Component.translatable("block.ae2lt.overloaded_power_supply"))
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(
                    AE2LightningTech.MODID, "overloaded_power_supply"));

    @GuiSync(0)
    public long bufferCapacity;
    @GuiSync(1)
    public long bufferedEnergy;
    @GuiSync(2)
    public int connectionCount;
    @GuiSync(3)
    public int modeOrdinal;
    @GuiSync(4)
    public int ticketCount;
    @GuiSync(5)
    public int statusOrdinal;
    @GuiSync(6)
    public long lastTransferAmount;

    private final OverloadedPowerSupplyBlockEntity host;
    private final Slot cellSlot;

    public OverloadedPowerSupplyMenu(int id, Inventory playerInventory, OverloadedPowerSupplyBlockEntity host) {
        super(TYPE, id, playerInventory, host);
        this.host = host;

        this.cellSlot = addSlot(
                new AppEngSlot(host.getCellInventory(), 0),
                Ae2ltSlotSemantics.OVERLOADED_POWER_SUPPLY_CELL);

        createPlayerInventorySlots(playerInventory);
        registerClientAction("cycleMode", this::cycleMode);
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            bufferCapacity = host.getBufferCapacity();
            bufferedEnergy = host.getSupplyLogic().getBufferedEnergy();
            connectionCount = host.getConnections().size();
            modeOrdinal = host.getMode().ordinal();
            ticketCount = host.getSupplyLogic().getActiveTicketCount();
            statusOrdinal = host.getSupplyLogic().getLastStatus().ordinal();
            lastTransferAmount = host.getSupplyLogic().getLastTransferAmount();
        }
        super.broadcastChanges();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (isClientSide() || index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }

        Slot sourceSlot = getSlot(index);
        if (!sourceSlot.hasItem() || !sourceSlot.mayPickup(player)) {
            return ItemStack.EMPTY;
        }

        ItemStack sourceStack = sourceSlot.getItem();
        ItemStack original = sourceStack.copy();
        ItemStack remainder;

        if (isPlayerSideSlot(sourceSlot)) {
            if (!AppFluxBridge.isFluxCell(sourceStack)) {
                return ItemStack.EMPTY;
            }
            remainder = moveIntoSlots(sourceStack.copy(), List.of(cellSlot));
        } else {
            remainder = moveIntoSlots(sourceStack.copy(), getPlayerDestinationSlots());
        }

        int moved = original.getCount() - remainder.getCount();
        if (moved <= 0) {
            return ItemStack.EMPTY;
        }

        sourceSlot.remove(moved);
        sourceSlot.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        if (host.isRemoved() || host.getLevel() == null) {
            return false;
        }

        return host.getLevel().getBlockEntity(host.getBlockPos()) == host
                && player.level() == host.getLevel()
                && player.distanceToSqr(
                        host.getBlockPos().getX() + 0.5D,
                        host.getBlockPos().getY() + 0.5D,
                        host.getBlockPos().getZ() + 0.5D) <= 64.0D;
    }

    public void clientCycleMode() {
        sendClientAction("cycleMode");
    }

    public Component getModeButtonMessage() {
        return getMode() == OverloadedPowerSupplyBlockEntity.PowerMode.OVERLOAD
                ? Component.translatable("ae2lt.gui.overloaded_power_supply.mode.overload")
                : Component.translatable("ae2lt.gui.overloaded_power_supply.mode.normal");
    }

    public Component getConnectionsMessage() {
        return Component.translatable("ae2lt.gui.overloaded_power_supply.connections", connectionCount);
    }

    public Component getBufferMessage() {
        return Component.translatable(
                "ae2lt.gui.overloaded_power_supply.buffer",
                Long.toString(bufferedEnergy),
                Long.toString(bufferCapacity));
    }

    public Component getTicketsMessage() {
        return Component.translatable("ae2lt.gui.overloaded_power_supply.tickets", ticketCount);
    }

    public Component getCellMessage() {
        return bufferCapacity > 0L
                ? Component.translatable("ae2lt.gui.overloaded_power_supply.cell_present")
                : Component.translatable("ae2lt.gui.overloaded_power_supply.no_cell");
    }

    public Component getStatusMessage() {
        return switch (getStatus()) {
            case APPFLUX_UNAVAILABLE ->
                    Component.translatable("ae2lt.gui.overloaded_power_supply.status.appflux_unavailable");
            case NO_CELL ->
                    Component.translatable("ae2lt.gui.overloaded_power_supply.status.no_cell");
            case NO_GRID ->
                    Component.translatable("ae2lt.gui.overloaded_power_supply.status.no_grid");
            case NO_CONNECTIONS ->
                    Component.translatable("ae2lt.gui.overloaded_power_supply.status.no_connections");
            case NO_VALID_TARGETS ->
                    Component.translatable("ae2lt.gui.overloaded_power_supply.status.no_valid_targets");
            case NO_NETWORK_FE ->
                    Component.translatable("ae2lt.gui.overloaded_power_supply.status.no_network_fe");
            case TARGET_UNSUPPORTED ->
                    Component.translatable("ae2lt.gui.overloaded_power_supply.status.target_unsupported");
            case TARGET_BLOCKED ->
                    Component.translatable("ae2lt.gui.overloaded_power_supply.status.target_blocked");
            case ACTIVE ->
                    Component.translatable(
                            "ae2lt.gui.overloaded_power_supply.status.active",
                            Long.toString(lastTransferAmount));
            case IDLE ->
                    Component.translatable("ae2lt.gui.overloaded_power_supply.status.idle");
        };
    }

    public OverloadedPowerSupplyLogic.Status getStatus() {
        return statusOrdinal >= 0 && statusOrdinal < OverloadedPowerSupplyLogic.Status.values().length
                ? OverloadedPowerSupplyLogic.Status.values()[statusOrdinal]
                : OverloadedPowerSupplyLogic.Status.IDLE;
    }

    public OverloadedPowerSupplyBlockEntity.PowerMode getMode() {
        return modeOrdinal >= 0 && modeOrdinal < OverloadedPowerSupplyBlockEntity.PowerMode.values().length
                ? OverloadedPowerSupplyBlockEntity.PowerMode.values()[modeOrdinal]
                : OverloadedPowerSupplyBlockEntity.PowerMode.NORMAL;
    }

    private void cycleMode() {
        if (!isServerSide()) {
            return;
        }
        host.cycleMode();
        broadcastChanges();
    }

    private List<Slot> getPlayerDestinationSlots() {
        var result = new ArrayList<Slot>(getSlots(SlotSemantics.PLAYER_INVENTORY));
        result.addAll(getSlots(SlotSemantics.PLAYER_HOTBAR));
        return result;
    }

    private static ItemStack moveIntoSlots(ItemStack stack, List<Slot> destinations) {
        ItemStack remainder = stack;
        for (Slot slot : destinations) {
            if (!slot.hasItem()) {
                continue;
            }
            remainder = slot.safeInsert(remainder);
            if (remainder.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }
        for (Slot slot : destinations) {
            if (slot.hasItem()) {
                continue;
            }
            remainder = slot.safeInsert(remainder);
            if (remainder.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }
        return remainder;
    }
}
