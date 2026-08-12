package com.moakiee.ae2lt.menu;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

import appeng.api.orientation.RelativeSide;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.slot.AppEngSlot;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.blockentity.CrystalCatalyzerBlockEntity;
import com.moakiee.ae2lt.machine.crystalcatalyzer.CrystalCatalyzerInventory;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.Mode;

public class CrystalCatalyzerMenu extends AEBaseMenu implements FrequencyBindingMenu {
    public static final MenuType<CrystalCatalyzerMenu> TYPE = MenuTypeBuilder
            .create(CrystalCatalyzerMenu::new, CrystalCatalyzerBlockEntity.class)
            .withMenuTitle(host -> Component.translatable("block.ae2lt.crystal_catalyzer"))
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(
                    AE2LightningTech.MODID,
                    "crystal_catalyzer"));

    @GuiSync(20)
    public long storedEnergy;
    @GuiSync(21)
    public long consumedEnergy;
    @GuiSync(22)
    public long totalEnergy;
    @GuiSync(23)
    public boolean working;
    @GuiSync(24)
    public int fluidId = -1;
    @GuiSync(25)
    public int fluidAmount;
    @GuiSync(26)
    public boolean autoExport;
    @GuiSync(27)
    public int outputSideMask;
    @GuiSync(28)
    public int modeOrdinal;
    @GuiSync(29)
    public long highVoltageAvailable;
    @GuiSync(30)
    public long extremeHighVoltageAvailable;

    private static final RelativeSide[] OUTPUT_SIDES = RelativeSide.values();

    private final CrystalCatalyzerBlockEntity host;
    private final Slot catalystSlot;
    private final Slot matrixSlot;
    private final Slot outputSlot;

    public CrystalCatalyzerMenu(int id, Inventory playerInventory, CrystalCatalyzerBlockEntity host) {
        super(TYPE, id, playerInventory, host);
        this.host = host;

        var inventory = host.getInventory();
        this.catalystSlot = addSlot(
                new LargeStackAppEngSlot(inventory, CrystalCatalyzerInventory.SLOT_CATALYST),
                Ae2ltSlotSemantics.CRYSTAL_CATALYZER_CATALYST);
        this.matrixSlot = addSlot(
                new AppEngSlot(inventory, CrystalCatalyzerInventory.SLOT_MATRIX),
                Ae2ltSlotSemantics.CRYSTAL_CATALYZER_MATRIX);
        Ae2ltSlotBackgrounds.withBackground(this.matrixSlot, Ae2ltSlotBackgrounds.LIGHTNING_COLLAPSE_MATRIX);
        this.outputSlot = addSlot(
                new LargeStackAppEngSlot(inventory, CrystalCatalyzerInventory.SLOT_OUTPUT),
                SlotSemantics.MACHINE_OUTPUT);

        createPlayerInventorySlots(playerInventory);

        registerClientAction("toggleAutoExport", this::toggleAutoExport);
        registerClientAction("toggleOutputSide", Integer.class, this::toggleOutputSide);
        registerClientAction("clearOutputSides", this::clearOutputSides);
        registerClientAction("insertFluid", this::insertFluidFromCarried);
        registerClientAction("extractFluid", this::extractFluidToCarried);
        registerClientAction("clearFluidTank", this::clearFluidTank);
        registerClientAction("cycleMode", this::cycleMode);
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            storedEnergy = host.getEnergyStorage().getStoredEnergyLong();
            consumedEnergy = host.getConsumedEnergy();
            totalEnergy = host.getLockedRecipe().map(CrystalCatalyzerMenu::lockedRecipeEnergy).orElse(0L);
            working = host.isWorking();

            var fluid = host.getFluid();
            fluidId = fluid.isEmpty() ? -1 : BuiltInRegistries.FLUID.getId(fluid.getFluid());
            fluidAmount = fluid.getAmount();

            autoExport = host.isAutoExportEnabled();
            outputSideMask = toOutputSideMask(host.getAllowedOutputs());
            modeOrdinal = host.getMode().ordinal();
            highVoltageAvailable = host.getAvailableHighVoltage();
            extremeHighVoltageAvailable = host.getAvailableExtremeHighVoltage();
        }
        super.broadcastChanges();
    }

    private static long lockedRecipeEnergy(
            com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalCatalyzerLockedRecipe recipe) {
        return recipe.totalEnergy();
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

    public CrystalCatalyzerBlockEntity getHost() {
        return host;
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
        return CrystalCatalyzerBlockEntity.ENERGY_CAPACITY;
    }

    public int getFluidCapacity() {
        return CrystalCatalyzerBlockEntity.FLUID_TANK_CAPACITY_MB;
    }

    public boolean isWorking() {
        return working;
    }

    public double getProgress() {
        if (totalEnergy <= 0L) {
            return 0.0D;
        }
        return Math.min(1.0D, (double) consumedEnergy / (double) totalEnergy);
    }

    public FluidStack getFluid() {
        if (fluidId < 0 || fluidAmount <= 0) {
            return FluidStack.EMPTY;
        }

        Fluid fluid = BuiltInRegistries.FLUID.byId(fluidId);
        if (fluid == null || fluid == Fluids.EMPTY) {
            return FluidStack.EMPTY;
        }

        return new FluidStack(fluid, fluidAmount);
    }

    public boolean isAutoExportEnabled() {
        return autoExport;
    }

    public boolean isOutputSideEnabled(RelativeSide side) {
        return (outputSideMask & (1 << side.ordinal())) != 0;
    }

    public Mode getMode() {
        Mode[] values = Mode.values();
        return modeOrdinal >= 0 && modeOrdinal < values.length ? values[modeOrdinal] : Mode.CRYSTAL;
    }

    public long getHighVoltageAvailable() {
        return highVoltageAvailable;
    }

    public long getExtremeHighVoltageAvailable() {
        return extremeHighVoltageAvailable;
    }

    public void clientCycleMode() {
        sendClientAction("cycleMode");
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

    public void clientInsertFluid() {
        sendClientAction("insertFluid");
    }

    public void clientExtractFluid() {
        sendClientAction("extractFluid");
    }

    public void clientClearFluidTank() {
        sendClientAction("clearFluidTank");
    }

    private void insertFluidFromCarried() {
        if (!isServerSide()) {
            return;
        }
        host.tryInsertFluidFromCarried(getPlayer());
        broadcastChanges();
    }

    private void extractFluidToCarried() {
        if (!isServerSide()) {
            return;
        }
        host.tryExtractFluidToCarried(getPlayer());
        broadcastChanges();
    }

    private void clearFluidTank() {
        if (!isServerSide()) {
            return;
        }
        host.clearFluidTank();
        broadcastChanges();
    }

    private void toggleAutoExport() {
        if (!isServerSide()) {
            return;
        }
        host.setAutoExportEnabled(!host.isAutoExportEnabled());
    }

    private void cycleMode() {
        if (!isServerSide()) {
            return;
        }
        host.cycleMode();
        broadcastChanges();
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

    private static int toOutputSideMask(EnumSet<RelativeSide> sides) {
        int mask = 0;
        for (var side : sides) {
            mask |= 1 << side.ordinal();
        }
        return mask;
    }

    private ItemStack moveFromPlayerInventory(ItemStack stack) {
        return moveIntoSlots(stack, List.of(matrixSlot, catalystSlot));
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
