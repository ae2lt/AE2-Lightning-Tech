package com.moakiee.ae2lt.menu;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import appeng.api.orientation.RelativeSide;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.ToolboxMenu;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.blockentity.LightningSimulationChamberBlockEntity;
import com.moakiee.ae2lt.machine.lightningchamber.LightningSimulationChamberInventory;
import com.moakiee.ae2lt.machine.lightningchamber.recipe.LightningSimulationRecipeService;
import com.moakiee.ae2lt.me.key.LightningKey;

public class LightningSimulationChamberMenu extends AEBaseMenu implements FrequencyBindingMenu {
    public static final MenuType<LightningSimulationChamberMenu> TYPE = MenuTypeBuilder
            .create(LightningSimulationChamberMenu::new, LightningSimulationChamberBlockEntity.class)
            .withMenuTitle(host -> Component.translatable("block.ae2lt.lightning_simulation_room"))
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(
                    AE2LightningTech.MODID,
                    "lightning_simulation_room"));

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

    @GuiSync(28)
    public int lightningTierOrdinal = -1;

    @GuiSync(29)
    public int lightningCost;

    @GuiSync(30)
    public boolean matrixInstalled;

    @GuiSync(31)
    public boolean matrixSubstitutionActive;

    @GuiSync(32)
    public long equivalentHighVoltageCost;

    @GuiSync(33)
    public boolean lightningInsufficient;

    private final LightningSimulationChamberBlockEntity host;
    private final List<Slot> machineInputSlots = new ArrayList<>(3);
    private final Slot catalystSlot;
    private final ToolboxMenu toolbox;

    public LightningSimulationChamberMenu(int id, Inventory playerInventory, LightningSimulationChamberBlockEntity host) {
        super(TYPE, id, playerInventory, host);
        this.host = host;
        // 网络工具 toolbox：手持网络工具时在 GUI 右侧暴露 9 格升级卡槽
        this.toolbox = new ToolboxMenu(this);

        addMachineSlots();
        this.catalystSlot = addSlot(
                new LargeStackAppEngSlot(host.getInventory(), LightningSimulationChamberInventory.SLOT_CATALYST),
                Ae2ltSlotSemantics.LIGHTNING_SIMULATION_CATALYST);
        Ae2ltSlotBackgrounds.withBackground(this.catalystSlot, Ae2ltSlotBackgrounds.LIGHTNING_COLLAPSE_MATRIX);
        addSlot(
                new LargeStackAppEngSlot(host.getInventory(), LightningSimulationChamberInventory.SLOT_OUTPUT),
                SlotSemantics.MACHINE_OUTPUT);

        setupUpgrades(host.getUpgrades());
        createPlayerInventorySlots(playerInventory);

        registerClientAction("toggleAutoExport", this::toggleAutoExport);
        registerClientAction("toggleOutputSide", Integer.class, this::toggleOutputSide);
        registerClientAction("clearOutputSides", this::clearOutputSides);
    }

    private void addMachineSlots() {
        machineInputSlots.add(addSlot(
                new LargeStackAppEngSlot(host.getInventory(), LightningSimulationChamberInventory.SLOT_INPUT_0),
                SlotSemantics.MACHINE_INPUT));
        machineInputSlots.add(addSlot(
                new LargeStackAppEngSlot(host.getInventory(), LightningSimulationChamberInventory.SLOT_INPUT_1),
                SlotSemantics.MACHINE_INPUT));
        machineInputSlots.add(addSlot(
                new LargeStackAppEngSlot(host.getInventory(), LightningSimulationChamberInventory.SLOT_INPUT_2),
                SlotSemantics.MACHINE_INPUT));
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
            matrixInstalled = host.hasLightningCollapseMatrix();

            var lockedRecipe = host.getLockedRecipe().orElse(null);
            var processableRecipe = lockedRecipe == null
                    ? host.findProcessableRecipe().map(candidate -> candidate.recipe().value()).orElse(null)
                    : null;
            if (lockedRecipe != null) {
                lightningTierOrdinal = lockedRecipe.lightningTier().ordinal();
                lightningCost = lockedRecipe.lightningCost();
            } else if (processableRecipe != null) {
                lightningTierOrdinal = processableRecipe.lightningTier().ordinal();
                lightningCost = processableRecipe.lightningCost();
            } else {
                lightningTierOrdinal = -1;
                lightningCost = 0;
            }

            if (lightningTierOrdinal >= 0 && lightningCost > 0) {
                var tier = LightningKey.Tier.fromOrdinal(lightningTierOrdinal);
                var plan = LightningSimulationRecipeService.resolveLightningConsumption(
                        host.getInventory(),
                        tier,
                        lightningCost,
                        highVoltageAvailable,
                        extremeHighVoltageAvailable);
                matrixSubstitutionActive = plan
                        .map(LightningSimulationRecipeService.LightningConsumptionPlan::matrixSubstitution)
                        .orElse(false);
                equivalentHighVoltageCost = LightningSimulationRecipeService.getEquivalentHighVoltageCost(
                        tier,
                        lightningCost);
                lightningInsufficient = lockedRecipe != null && plan.isEmpty();
            } else {
                matrixSubstitutionActive = false;
                equivalentHighVoltageCost = 0L;
                lightningInsufficient = false;
            }
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
        return LightningSimulationChamberBlockEntity.ENERGY_CAPACITY;
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

    public Component getLightningDemandMessage() {
        if (lightningTierOrdinal < 0 || lightningCost <= 0) {
            return Component.translatable("ae2lt.gui.lightning_simulation.demand.none");
        }

        return Component.translatable(
                "ae2lt.gui.lightning_simulation.demand",
                lightningCost,
                Component.translatable(getLightningTierTranslationKey()));
    }

    public Component getMatrixMessage() {
        return Component.translatable(
                "ae2lt.gui.lightning_simulation.matrix.label",
                Component.translatable(matrixInstalled
                        ? "ae2lt.gui.lightning_simulation.matrix.installed"
                        : "ae2lt.gui.lightning_simulation.matrix.missing"));
    }

    public Component getSubstitutionMessage() {
        if (lightningTierOrdinal < 0 || lightningCost <= 0) {
            return Component.translatable("ae2lt.gui.lightning_simulation.substitution.none");
        }

        if (matrixSubstitutionActive) {
            return Component.translatable(
                    "ae2lt.gui.lightning_simulation.substitution.active",
                    equivalentHighVoltageCost);
        }

        if (matrixInstalled && LightningKey.Tier.fromOrdinal(lightningTierOrdinal) == LightningKey.Tier.EXTREME_HIGH_VOLTAGE) {
            return Component.translatable(
                    "ae2lt.gui.lightning_simulation.substitution.available",
                    equivalentHighVoltageCost);
        }

        return Component.translatable("ae2lt.gui.lightning_simulation.substitution.inactive");
    }

    public boolean isLightningInsufficient() {
        return lightningInsufficient;
    }

    public Component getStatusMessage() {
        return Component.translatable(
                "ae2lt.gui.lightning_simulation.status.label",
                Component.translatable(lightningInsufficient
                        ? "ae2lt.gui.lightning_simulation.status.waiting_lightning"
                        : "ae2lt.gui.lightning_simulation.status.normal"));
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

    public LightningSimulationChamberBlockEntity getHost() {
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

    private String getLightningTierTranslationKey() {
        return LightningKey.Tier.fromOrdinal(lightningTierOrdinal) == LightningKey.Tier.EXTREME_HIGH_VOLTAGE
                ? "ae2lt.gui.lightning_simulation.tier.extreme_high_voltage"
                : "ae2lt.gui.lightning_simulation.tier.high_voltage";
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
