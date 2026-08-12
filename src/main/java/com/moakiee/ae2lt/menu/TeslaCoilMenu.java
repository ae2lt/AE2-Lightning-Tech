package com.moakiee.ae2lt.menu;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import appeng.menu.AEBaseMenu;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.blockentity.TeslaCoilBlockEntity;
import com.moakiee.ae2lt.machine.teslacoil.TeslaCoilInventory;
import com.moakiee.ae2lt.machine.teslacoil.TeslaCoilMode;
import com.moakiee.ae2lt.machine.teslacoil.TeslaCoilStatus;

public class TeslaCoilMenu extends AEBaseMenu implements FrequencyBindingMenu {
    public static final MenuType<TeslaCoilMenu> TYPE = MenuTypeBuilder
            .create(TeslaCoilMenu::new, TeslaCoilBlockEntity.class)
            .withMenuTitle(host -> Component.translatable("block.ae2lt.tesla_coil"))
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(
                    AE2LightningTech.MODID,
                    "tesla_coil"));

    @GuiSync(40)
    public long storedEnergy;

    @GuiSync(41)
    public long consumedEnergy;

    @GuiSync(42)
    public long totalEnergy;

    @GuiSync(43)
    public boolean working;

    @GuiSync(44)
    public int modeOrdinal;

    @GuiSync(45)
    public int statusOrdinal;

    @GuiSync(46)
    public long highVoltageAvailable;

    @GuiSync(47)
    public long extremeHighVoltageAvailable;

    @GuiSync(48)
    public boolean matrixInstalled;

    private final TeslaCoilBlockEntity host;
    private final Slot dustSlot;
    private final Slot matrixSlot;

    public TeslaCoilMenu(int id, Inventory playerInventory, TeslaCoilBlockEntity host) {
        super(TYPE, id, playerInventory, host);
        this.host = host;

        this.dustSlot = addSlot(
                new LargeStackAppEngSlot(host.getInventory(), TeslaCoilInventory.SLOT_DUST),
                Ae2ltSlotSemantics.TESLA_COIL_DUST);
        this.matrixSlot = addSlot(
                new LargeStackAppEngSlot(host.getInventory(), TeslaCoilInventory.SLOT_MATRIX),
                Ae2ltSlotSemantics.TESLA_COIL_MATRIX);
        Ae2ltSlotBackgrounds.withBackground(this.matrixSlot, Ae2ltSlotBackgrounds.LIGHTNING_COLLAPSE_MATRIX);

        createPlayerInventorySlots(playerInventory);

        registerClientAction("cycleMode", this::cycleMode);
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            storedEnergy = host.getEnergyStorage().getStoredEnergyLong();
            consumedEnergy = host.getConsumedEnergy();
            totalEnergy = host.hasLockedMode() ? host.getCurrentTotalEnergy() : 0L;
            working = host.isWorking();
            modeOrdinal = host.getSelectedMode().ordinal();
            statusOrdinal = host.getStatus().ordinal();
            highVoltageAvailable = host.getAvailableHighVoltage();
            extremeHighVoltageAvailable = host.getAvailableExtremeHighVoltage();
            matrixInstalled = host.isMatrixInstalled();
        }

        super.broadcastChanges();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int idx) {
        if (isClientSide() || idx < 0 || idx >= slots.size()) {
            return ItemStack.EMPTY;
        }

        var sourceSlot = getSlot(idx);
        if (!sourceSlot.hasItem() || !sourceSlot.mayPickup(player)) {
            return ItemStack.EMPTY;
        }

        var sourceStack = sourceSlot.getItem();
        var original = sourceStack.copy();
        ItemStack remainder;

        if (isPlayerSideSlot(sourceSlot)) {
            remainder = moveFromPlayerInventory(sourceStack.copy());
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
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (clickType == ClickType.PICKUP && handleLargeMachineSlotPickup(slotId, button, player)) {
            broadcastChanges();
            return;
        }

        super.clicked(slotId, button, clickType, player);
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

    public long getStoredEnergy() {
        return storedEnergy;
    }

    public long getConsumedEnergy() {
        return consumedEnergy;
    }

    public long getTotalEnergy() {
        return totalEnergy;
    }

    public long getEnergyCapacity() {
        return TeslaCoilBlockEntity.ENERGY_CAPACITY;
    }

    public double getProgress() {
        if (totalEnergy <= 0L) {
            return 0.0D;
        }

        return Math.min(1.0D, (double) consumedEnergy / (double) totalEnergy);
    }

    public TeslaCoilMode getMode() {
        return TeslaCoilMode.fromOrdinal(modeOrdinal);
    }

    public TeslaCoilStatus getStatus() {
        return TeslaCoilStatus.fromOrdinal(statusOrdinal);
    }

    public boolean isMatrixInstalled() {
        return matrixInstalled;
    }

    public Component getModeButtonMessage() {
        return Component.translatable("ae2lt.gui.tesla_coil.mode.button",
                Component.translatable(getMode().translationKey()));
    }

    public Component getStatusMessage() {
        return Component.translatable("ae2lt.gui.tesla_coil.status.label",
                Component.translatable(getStatus().translationKey()));
    }

    public Component getMatrixMessage() {
        return Component.translatable(
                "ae2lt.gui.tesla_coil.matrix.label",
                Component.translatable(isMatrixInstalled()
                        ? "ae2lt.gui.tesla_coil.matrix.installed"
                        : "ae2lt.gui.tesla_coil.matrix.missing"));
    }

    public long getHighVoltageAvailable() {
        return highVoltageAvailable;
    }

    public long getExtremeHighVoltageAvailable() {
        return extremeHighVoltageAvailable;
    }

    public Component getHighVoltageMessage() {
        return Component.translatable("ae2lt.gui.lightning_status.high_voltage", highVoltageAvailable);
    }

    public Component getExtremeHighVoltageMessage() {
        return Component.translatable("ae2lt.gui.lightning_status.extreme_high_voltage", extremeHighVoltageAvailable);
    }

    public void clientCycleMode() {
        sendClientAction("cycleMode");
    }

    public TeslaCoilBlockEntity getHost() {
        return host;
    }

    private void cycleMode() {
        if (!isServerSide()) {
            return;
        }

        host.cycleMode();
    }

    private ItemStack moveFromPlayerInventory(ItemStack stack) {
        if (host.getInventory().isLightningCollapseMatrix(stack)) {
            return moveIntoSlots(stack, List.of(matrixSlot));
        }

        if (host.getInventory().isOverloadCrystalDust(stack)) {
            return moveIntoSlots(stack, List.of(dustSlot));
        }

        return stack;
    }

    private List<Slot> getPlayerDestinationSlots() {
        var result = new ArrayList<Slot>(getSlots(appeng.menu.SlotSemantics.PLAYER_INVENTORY));
        result.addAll(getSlots(appeng.menu.SlotSemantics.PLAYER_HOTBAR));
        return result;
    }

    private static ItemStack moveIntoSlots(ItemStack stack, List<Slot> destinations) {
        ItemStack remainder = stack;

        for (var slot : destinations) {
            if (!slot.hasItem()) {
                continue;
            }

            remainder = slot.safeInsert(remainder);
            if (remainder.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }

        for (var slot : destinations) {
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

    private boolean handleLargeMachineSlotPickup(int slotId, int button, Player player) {
        if (slotId < 0 || slotId >= slots.size()) {
            return false;
        }

        var slot = getSlot(slotId);
        if (!(slot instanceof LargeStackAppEngSlot) || isPlayerSideSlot(slot)) {
            return false;
        }

        if (button != 0 && button != 1) {
            return false;
        }

        var carried = getCarried();
        var slotStack = slot.getItem();
        boolean rightClick = button == 1;

        if (carried.isEmpty()) {
            if (slotStack.isEmpty() || !slot.mayPickup(player)) {
                return true;
            }

            int requested = rightClick
                    ? Math.min(64, Math.max(1, (int) Math.ceil(slotStack.getCount() / 2.0D)))
                    : 64;
            var taken = slot.remove(requested);
            setCarried(taken);
            slot.onTake(player, taken);
            slot.setChanged();
            return true;
        }

        if (!slot.mayPlace(carried)) {
            return false;
        }

        if (slotStack.isEmpty()) {
            int toMove = Math.min(rightClick ? 1 : carried.getCount(), slot.getMaxStackSize(carried));
            if (toMove <= 0) {
                return true;
            }

            var placed = carried.copyWithCount(toMove);
            slot.set(placed);
            carried.shrink(toMove);
            setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
            return true;
        }

        if (ItemStack.isSameItemSameComponents(slotStack, carried)) {
            int room = slot.getMaxStackSize(carried) - slotStack.getCount();
            int toMove = Math.min(rightClick ? 1 : carried.getCount(), room);
            if (toMove <= 0) {
                return true;
            }

            slotStack.grow(toMove);
            slot.setChanged();
            carried.shrink(toMove);
            setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
            return true;
        }

        if (!slot.mayPickup(player)
                || carried.getCount() > slot.getMaxStackSize(carried)
                || slotStack.getCount() > 64) {
            return true;
        }

        slot.set(carried);
        setCarried(slotStack);
        return true;
    }
}
