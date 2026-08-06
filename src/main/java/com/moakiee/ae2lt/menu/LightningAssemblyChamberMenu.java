package com.moakiee.ae2lt.menu;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import appeng.api.orientation.RelativeSide;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.ToolboxMenu;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.blockentity.LightningAssemblyChamberBlockEntity;
import com.moakiee.ae2lt.machine.lightningassembly.LightningAssemblyChamberInventory;

public class LightningAssemblyChamberMenu extends AEBaseMenu implements FrequencyBindingMenu {
    public static final MenuType<LightningAssemblyChamberMenu> TYPE = MenuTypeBuilder
            .create(LightningAssemblyChamberMenu::new, LightningAssemblyChamberBlockEntity.class)
            .withMenuTitle(host -> Component.translatable("block.ae2lt.lightning_assembly_chamber"))
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(
                    AE2LightningTech.MODID,
                    "lightning_assembly_chamber"));

    private static final RelativeSide[] OUTPUT_SIDES = RelativeSide.values();

    @GuiSync(20)
    public long storedEnergy;

    @GuiSync(21)
    public long consumedEnergy;

    @GuiSync(22)
    public long totalEnergy;

    @GuiSync(23)
    public boolean working;

    @GuiSync(24)
    public boolean autoExport;

    @GuiSync(25)
    public int outputSideMask;

    @GuiSync(26)
    public long highVoltageAvailable;

    @GuiSync(27)
    public long extremeHighVoltageAvailable;

    private final LightningAssemblyChamberBlockEntity host;
    private final List<Slot> machineInputSlots = new ArrayList<>(9);
    private final Slot catalystSlot;
    private final ToolboxMenu toolbox;

    public LightningAssemblyChamberMenu(int id, Inventory playerInventory, LightningAssemblyChamberBlockEntity host) {
        super(TYPE, id, playerInventory, host);
        this.host = host;
        // 网络工具 toolbox：手持网络工具时在 GUI 右侧暴露 9 格升级卡槽
        this.toolbox = new ToolboxMenu(this);

        addMachineSlots();
        this.catalystSlot = addSlot(
                new LargeStackAppEngSlot(host.getInventory(), LightningAssemblyChamberInventory.SLOT_CATALYST),
                Ae2ltSlotSemantics.LIGHTNING_ASSEMBLY_CATALYST);
        Ae2ltSlotBackgrounds.withBackground(this.catalystSlot, Ae2ltSlotBackgrounds.LIGHTNING_COLLAPSE_MATRIX);
        addSlot(
                new LargeStackAppEngSlot(host.getInventory(), LightningAssemblyChamberInventory.SLOT_OUTPUT),
                SlotSemantics.MACHINE_OUTPUT);

        setupUpgrades(host.getUpgrades());
        createPlayerInventorySlots(playerInventory);

        registerClientAction("toggleAutoExport", this::toggleAutoExport);
        registerClientAction("toggleOutputSide", Integer.class, this::toggleOutputSide);
        registerClientAction("clearOutputSides", this::clearOutputSides);
    }

    private void addMachineSlots() {
        machineInputSlots.add(addSlot(
                new LargeStackAppEngSlot(host.getInventory(), LightningAssemblyChamberInventory.SLOT_INPUT_0),
                Ae2ltSlotSemantics.LIGHTNING_ASSEMBLY_INPUT_0));
        machineInputSlots.add(addSlot(
                new LargeStackAppEngSlot(host.getInventory(), LightningAssemblyChamberInventory.SLOT_INPUT_1),
                Ae2ltSlotSemantics.LIGHTNING_ASSEMBLY_INPUT_1));
        machineInputSlots.add(addSlot(
                new LargeStackAppEngSlot(host.getInventory(), LightningAssemblyChamberInventory.SLOT_INPUT_2),
                Ae2ltSlotSemantics.LIGHTNING_ASSEMBLY_INPUT_2));
        machineInputSlots.add(addSlot(
                new LargeStackAppEngSlot(host.getInventory(), LightningAssemblyChamberInventory.SLOT_INPUT_3),
                Ae2ltSlotSemantics.LIGHTNING_ASSEMBLY_INPUT_3));
        machineInputSlots.add(addSlot(
                new LargeStackAppEngSlot(host.getInventory(), LightningAssemblyChamberInventory.SLOT_INPUT_4),
                Ae2ltSlotSemantics.LIGHTNING_ASSEMBLY_INPUT_4));
        machineInputSlots.add(addSlot(
                new LargeStackAppEngSlot(host.getInventory(), LightningAssemblyChamberInventory.SLOT_INPUT_5),
                Ae2ltSlotSemantics.LIGHTNING_ASSEMBLY_INPUT_5));
        machineInputSlots.add(addSlot(
                new LargeStackAppEngSlot(host.getInventory(), LightningAssemblyChamberInventory.SLOT_INPUT_6),
                Ae2ltSlotSemantics.LIGHTNING_ASSEMBLY_INPUT_6));
        machineInputSlots.add(addSlot(
                new LargeStackAppEngSlot(host.getInventory(), LightningAssemblyChamberInventory.SLOT_INPUT_7),
                Ae2ltSlotSemantics.LIGHTNING_ASSEMBLY_INPUT_7));
        machineInputSlots.add(addSlot(
                new LargeStackAppEngSlot(host.getInventory(), LightningAssemblyChamberInventory.SLOT_INPUT_8),
                Ae2ltSlotSemantics.LIGHTNING_ASSEMBLY_INPUT_8));
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            toolbox.tick();
            storedEnergy = host.getEnergyStorage().getStoredEnergyLong();
            consumedEnergy = host.getConsumedEnergy();
            totalEnergy = host.getLockedRecipe().map(lockedRecipe -> lockedRecipe.totalEnergy()).orElse(0L);
            working = host.isWorking();
            autoExport = host.isAutoExportEnabled();
            outputSideMask = toOutputSideMask(host.getAllowedOutputs());
            highVoltageAvailable = host.getAvailableHighVoltage();
            extremeHighVoltageAvailable = host.getAvailableExtremeHighVoltage();
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
        return LightningAssemblyChamberBlockEntity.ENERGY_CAPACITY;
    }

    public boolean isWorking() {
        return working;
    }

    public boolean isAutoExportEnabled() {
        return autoExport;
    }

    public boolean isOutputSideEnabled(RelativeSide side) {
        return (outputSideMask & (1 << side.ordinal())) != 0;
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

    public double getProgress() {
        if (totalEnergy <= 0L) {
            return 0.0D;
        }

        return Math.min(1.0D, (double) consumedEnergy / (double) totalEnergy);
    }

    public void clientToggleAutoExport() {
        sendClientAction("toggleAutoExport");
    }

    public void clientToggleOutputSide(RelativeSide side) {
        sendClientAction("toggleOutputSide", side.ordinal());
    }

    public void clientClearOutputSides() {
        sendClientAction("clearOutputSides");
    }

    public LightningAssemblyChamberBlockEntity getHost() {
        return host;
    }

    public ToolboxMenu getToolbox() {
        return toolbox;
    }

    private void toggleAutoExport() {
        if (!isServerSide()) {
            return;
        }

        host.setAutoExportEnabled(!host.isAutoExportEnabled());
    }

    private void toggleOutputSide(Integer ordinal) {
        if (!isServerSide() || ordinal == null || ordinal < 0 || ordinal >= OUTPUT_SIDES.length) {
            return;
        }

        var updated = host.getAllowedOutputs();
        var side = OUTPUT_SIDES[ordinal];
        if (!updated.add(side)) {
            updated.remove(side);
        }
        host.updateOutputSides(updated);
    }

    private void clearOutputSides() {
        if (!isServerSide()) {
            return;
        }

        host.updateOutputSides(EnumSet.noneOf(RelativeSide.class));
    }

    private ItemStack moveFromPlayerInventory(ItemStack stack) {
        var upgradeSlots = getUpgradeDestinationSlots(stack);
        if (!upgradeSlots.isEmpty()) {
            return moveIntoSlots(stack, upgradeSlots);
        }

        return moveIntoSlots(stack, machineInputSlots);
    }

    private List<Slot> getUpgradeDestinationSlots(ItemStack stack) {
        var result = new ArrayList<Slot>();
        for (var slot : getSlots(SlotSemantics.UPGRADE)) {
            if (slot.mayPlace(stack)) {
                result.add(slot);
            }
        }
        return result;
    }

    private List<Slot> getPlayerDestinationSlots() {
        var result = new ArrayList<Slot>(getSlots(SlotSemantics.PLAYER_INVENTORY));
        result.addAll(getSlots(SlotSemantics.PLAYER_HOTBAR));
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

    private static int toOutputSideMask(EnumSet<RelativeSide> sides) {
        int mask = 0;
        for (var side : sides) {
            mask |= 1 << side.ordinal();
        }
        return mask;
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
