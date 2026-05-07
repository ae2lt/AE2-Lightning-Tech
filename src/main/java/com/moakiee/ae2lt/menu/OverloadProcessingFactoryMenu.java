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
import net.minecraftforge.fluids.FluidStack;

import appeng.api.orientation.RelativeSide;
import appeng.api.upgrades.Upgrades;
import appeng.core.localization.GuiText;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.ToolboxMenu;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.blockentity.OverloadProcessingFactoryBlockEntity;
import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.machine.overloadfactory.OverloadProcessingFactoryInventory;
import com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingRecipeCandidate;
import com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingRecipeService;
import com.moakiee.ae2lt.me.key.LightningKey;

public class OverloadProcessingFactoryMenu extends AEBaseMenu implements FrequencyBindingMenu {
    public static final MenuType<OverloadProcessingFactoryMenu> TYPE = MenuTypeBuilder
            .create(OverloadProcessingFactoryMenu::new, OverloadProcessingFactoryBlockEntity.class)
            .withMenuTitle(host -> Component.translatable("block.ae2lt.overload_processing_factory"))
            .buildUnregistered(new ResourceLocation(
                    AE2LightningTech.MODID,
                    "overload_processing_factory"));

    private static final List<SlotSemantic> INPUT_SEMANTICS = List.of(
            Ae2ltSlotSemantics.OVERLOAD_FACTORY_INPUT_0,
            Ae2ltSlotSemantics.OVERLOAD_FACTORY_INPUT_1,
            Ae2ltSlotSemantics.OVERLOAD_FACTORY_INPUT_2,
            Ae2ltSlotSemantics.OVERLOAD_FACTORY_INPUT_3,
            Ae2ltSlotSemantics.OVERLOAD_FACTORY_INPUT_4,
            Ae2ltSlotSemantics.OVERLOAD_FACTORY_INPUT_5,
            Ae2ltSlotSemantics.OVERLOAD_FACTORY_INPUT_6,
            Ae2ltSlotSemantics.OVERLOAD_FACTORY_INPUT_7,
            Ae2ltSlotSemantics.OVERLOAD_FACTORY_INPUT_8);
    private static final List<SlotSemantic> OUTPUT_SEMANTICS = List.of(
            Ae2ltSlotSemantics.OVERLOAD_FACTORY_OUTPUT_0);
    private static final RelativeSide[] OUTPUT_SIDES = RelativeSide.values();

    @GuiSync(80)
    public long storedEnergy;
    @GuiSync(81)
    public long consumedEnergy;
    @GuiSync(82)
    public long totalEnergy;
    @GuiSync(83)
    public boolean working;
    @GuiSync(84)
    public int currentParallel;
    @GuiSync(85)
    public int parallelCapacity;
    @GuiSync(86)
    public long highVoltageAvailable;
    @GuiSync(87)
    public long extremeHighVoltageAvailable;
    @GuiSync(88)
    public int lightningTierOrdinal = -1;
    @GuiSync(89)
    public long lightningCost;
    @GuiSync(90)
    public int inputFluidId = -1;
    @GuiSync(91)
    public int inputFluidAmount;
    @GuiSync(92)
    public int outputFluidId = -1;
    @GuiSync(93)
    public int outputFluidAmount;
    @GuiSync(94)
    public boolean matrixSubstitutionActive;
    @GuiSync(95)
    public long equivalentHighVoltageCost;

    @GuiSync(96)
    public boolean autoExport;

    @GuiSync(97)
    public int outputSideMask;

    private final OverloadProcessingFactoryBlockEntity host;
    private final List<Slot> machineInputSlots = new ArrayList<>(OverloadProcessingFactoryInventory.INPUT_SLOT_COUNT);
    private final Slot matrixSlot;
    private final ToolboxMenu toolbox;
    private int recipePreviewCooldown;
    private OverloadProcessingRecipeCandidate cachedProcessable;

    public OverloadProcessingFactoryMenu(int id, Inventory playerInventory, OverloadProcessingFactoryBlockEntity host) {
        super(TYPE, id, playerInventory, host);
        this.host = host;
        // 网络工具 toolbox：手持网络工具时在 GUI 右侧暴露 9 格升级卡槽
        this.toolbox = new ToolboxMenu(this);

        addMachineSlots();
        this.matrixSlot = addSlot(
                new LargeStackAppEngSlot(host.getInventory(), OverloadProcessingFactoryInventory.SLOT_MATRIX),
                Ae2ltSlotSemantics.OVERLOAD_FACTORY_MATRIX);
        Ae2ltSlotBackgrounds.withBackground(this.matrixSlot, Ae2ltSlotBackgrounds.LIGHTNING_COLLAPSE_MATRIX);
        setupUpgrades(host.getUpgrades());
        createPlayerInventorySlots(playerInventory);

        registerClientAction("toggleAutoExport", this::toggleAutoExport);
        registerClientAction("toggleOutputSide", Integer.class, this::toggleOutputSide);
        registerClientAction("clearOutputSides", this::clearOutputSides);
        registerClientAction("insertFluid", Integer.class, this::insertFluidFromCarried);
        registerClientAction("extractFluid", Integer.class, this::extractFluidToCarried);
        registerClientAction("clearFluidTank", Integer.class, this::clearFluidTank);
    }

    private void addMachineSlots() {
        for (int index = 0; index < OverloadProcessingFactoryInventory.INPUT_SLOT_COUNT; index++) {
            machineInputSlots.add(addSlot(
                    new LargeStackAppEngSlot(host.getInventory(), OverloadProcessingFactoryInventory.SLOT_INPUT_0 + index),
                    INPUT_SEMANTICS.get(index)));
        }
        for (int index = 0; index < OverloadProcessingFactoryInventory.OUTPUT_SLOT_COUNT; index++) {
            addSlot(
                    new LargeStackAppEngSlot(host.getInventory(), OverloadProcessingFactoryInventory.SLOT_OUTPUT_0 + index),
                    OUTPUT_SEMANTICS.get(index));
        }
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            toolbox.tick();
            storedEnergy = host.getEnergyStorage().getStoredEnergyLong();
            consumedEnergy = host.getConsumedEnergy();
            totalEnergy = host.getLockedRecipe().map(lockedRecipe -> lockedRecipe.totalEnergy()).orElse(0L);
            working = host.isWorking();
            parallelCapacity = host.getInstalledParallelCapacity();
            highVoltageAvailable = host.getAvailableHighVoltage();
            extremeHighVoltageAvailable = host.getAvailableExtremeHighVoltage();
            autoExport = host.isAutoExportEnabled();
            outputSideMask = toOutputSideMask(host.getAllowedOutputs());

            var inputFluid = host.getInputFluid();
            inputFluidId = inputFluid.isEmpty() ? -1 : BuiltInRegistries.FLUID.getId(inputFluid.getFluid());
            inputFluidAmount = inputFluid.getAmount();

            var outputFluid = host.getOutputFluid();
            outputFluidId = outputFluid.isEmpty() ? -1 : BuiltInRegistries.FLUID.getId(outputFluid.getFluid());
            outputFluidAmount = outputFluid.getAmount();

            var lockedRecipe = host.getLockedRecipe().orElse(null);
            OverloadProcessingRecipeCandidate processable;
            if (lockedRecipe == null) {
                if (recipePreviewCooldown <= 0) {
                    cachedProcessable = host.findProcessableRecipe().orElse(null);
                    recipePreviewCooldown = 10;
                } else {
                    recipePreviewCooldown--;
                }
                processable = cachedProcessable;
            } else {
                processable = null;
                cachedProcessable = null;
                recipePreviewCooldown = 0;
            }
            if (lockedRecipe != null) {
                currentParallel = lockedRecipe.parallel();
                lightningTierOrdinal = lockedRecipe.lightningTier().ordinal();
                lightningCost = lockedRecipe.totalLightningCost();
            } else if (processable != null) {
                currentParallel = processable.parallel();
                lightningTierOrdinal = processable.recipe().value().lightningTier().ordinal();
                lightningCost = processable.totalLightningCost();
                totalEnergy = processable.totalEnergy();
            } else {
                currentParallel = 0;
                lightningTierOrdinal = -1;
                lightningCost = 0L;
            }

            if (lightningTierOrdinal >= 0 && lightningCost > 0L) {
                var tier = LightningKey.Tier.fromOrdinal(lightningTierOrdinal);
                var plan = OverloadProcessingRecipeService.resolveLightningConsumption(
                        host.getInventory(),
                        tier,
                        lightningCost,
                        highVoltageAvailable,
                        extremeHighVoltageAvailable);
                matrixSubstitutionActive = plan
                        .map(OverloadProcessingRecipeService.LightningConsumptionPlan::matrixSubstitution)
                        .orElse(false);
                equivalentHighVoltageCost = OverloadProcessingRecipeService.getEquivalentHighVoltageCost(tier, lightningCost);
            } else {
                matrixSubstitutionActive = false;
                equivalentHighVoltageCost = 0L;
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

    public OverloadProcessingFactoryBlockEntity getHost() {
        return host;
    }

    @Override
    public net.minecraft.core.BlockPos getFrequencyBindingBlockPos() {
        return host.getBlockPos();
    }

    public ToolboxMenu getToolbox() {
        return toolbox;
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
        return AE2LTCommonConfig.overloadFactoryEnergyCapacity();
    }

    public int getInputTankCapacity() {
        return OverloadProcessingFactoryBlockEntity.INPUT_TANK_CAPACITY;
    }

    public int getOutputTankCapacity() {
        return OverloadProcessingFactoryBlockEntity.OUTPUT_TANK_CAPACITY;
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

    public double getProgress() {
        if (totalEnergy <= 0L) {
            return 0.0D;
        }
        return Math.min(1.0D, (double) consumedEnergy / (double) totalEnergy);
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

    public FluidStack getInputFluid() {
        return getFluid(inputFluidId, inputFluidAmount);
    }

    public FluidStack getOutputFluid() {
        return getFluid(outputFluidId, outputFluidAmount);
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

    public void clientInsertFluid(int tankIndex) {
        sendClientAction("insertFluid", tankIndex);
    }

    public void clientExtractFluid(int tankIndex) {
        sendClientAction("extractFluid", tankIndex);
    }

    public void clientClearFluidTank(int tankIndex) {
        sendClientAction("clearFluidTank", tankIndex);
    }

    private void insertFluidFromCarried(Integer tankIndex) {
        if (!isServerSide() || tankIndex == null) {
            return;
        }
        host.tryInsertFluidFromCarried(getPlayer(), tankIndex);
        broadcastChanges();
    }

    private void extractFluidToCarried(Integer tankIndex) {
        if (!isServerSide() || tankIndex == null) {
            return;
        }
        host.tryExtractFluidToCarried(getPlayer(), tankIndex);
        broadcastChanges();
    }

    private void clearFluidTank(Integer tankIndex) {
        if (!isServerSide() || tankIndex == null) {
            return;
        }
        host.clearFluidTank(tankIndex);
        broadcastChanges();
    }

    public List<Component> getCompatibleUpgradeLines() {
        var list = new ArrayList<Component>();
        list.add(GuiText.CompatibleUpgrades.text());
        list.addAll(Upgrades.getTooltipLinesForMachine(host.getUpgrades().getUpgradableItem()));
        return list;
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

    private FluidStack getFluid(int fluidId, int amount) {
        if (fluidId < 0 || amount <= 0) {
            return FluidStack.EMPTY;
        }

        Fluid fluid = BuiltInRegistries.FLUID.byId(fluidId);
        if (fluid == null || fluid == Fluids.EMPTY) {
            return FluidStack.EMPTY;
        }

        return new FluidStack(fluid, amount);
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

