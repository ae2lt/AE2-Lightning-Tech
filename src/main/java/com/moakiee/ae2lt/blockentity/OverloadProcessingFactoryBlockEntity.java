package com.moakiee.ae2lt.blockentity;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IStackWatcher;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageWatcherNode;
import appeng.api.stacks.AEKey;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.orientation.BlockOrientation;
import appeng.api.orientation.RelativeSide;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;

import com.moakiee.ae2lt.block.OverloadProcessingFactoryBlock;
import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.grid.FrequencyBindingHelper;
import com.moakiee.ae2lt.grid.FrequencyBindingHost;
import com.moakiee.ae2lt.logic.AdjacentItemAutoExportHelper;
import com.moakiee.ae2lt.logic.MemoryCardConfigSupport;
import com.moakiee.ae2lt.machine.common.GridRecipeMachineHost;
import com.moakiee.ae2lt.machine.common.LightningCollapseMatrixHost;
import com.moakiee.ae2lt.machine.overloadfactory.NotifyingFluidTank;
import com.moakiee.ae2lt.machine.overloadfactory.OverloadProcessingFactoryAutomationInventory;
import com.moakiee.ae2lt.machine.overloadfactory.OverloadProcessingFactoryEnergyStorage;
import com.moakiee.ae2lt.machine.overloadfactory.OverloadProcessingFactoryFluidHandler;
import com.moakiee.ae2lt.machine.overloadfactory.OverloadProcessingFactoryInventory;
import com.moakiee.ae2lt.machine.overloadfactory.OverloadProcessingFactoryLogic;
import com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingLockedRecipe;
import com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingRecipeCandidate;
import com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingRecipeService;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.menu.OverloadProcessingFactoryMenu;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModBlocks;

public class OverloadProcessingFactoryBlockEntity extends AENetworkedBlockEntity
    implements IUpgradeableObject, FrequencyBindingHost,
        LightningCollapseMatrixHost,
        GridRecipeMachineHost<OverloadProcessingLockedRecipe, OverloadProcessingRecipeCandidate> {
    private static final String TAG_INVENTORY = "Inventory";
    private static final String TAG_UPGRADES = "Upgrades";
    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_INPUT_TANK = "InputTank";
    private static final String TAG_OUTPUT_TANK = "OutputTank";
    private static final String TAG_CONSUMED_ENERGY = "ConsumedEnergy";
    private static final String TAG_PROCESSING_TICKS = "ProcessingTicks";
    private static final String TAG_LOCKED_RECIPE = "LockedRecipe";
    private static final String TAG_AUTO_EXPORT = "AutoExport";
    private static final String TAG_ALLOWED_OUTPUTS = "AllowedOutputs";

    public static final int INPUT_TANK_CAPACITY = 1_024_000;
    public static final int OUTPUT_TANK_CAPACITY = 1_024_000;
    public static final int SPEED_CARD_SLOTS = 4;

    private final OverloadProcessingFactoryInventory inventory =
            new OverloadProcessingFactoryInventory(this::onInventoryChanged);
    private final OverloadProcessingFactoryAutomationInventory automationInventory =
            new OverloadProcessingFactoryAutomationInventory(inventory);
    private final NotifyingFluidTank inputTank =
            new NotifyingFluidTank(INPUT_TANK_CAPACITY, this::onTankChanged);
    private final NotifyingFluidTank outputTank =
            new NotifyingFluidTank(OUTPUT_TANK_CAPACITY, this::onTankChanged);
    private final OverloadProcessingFactoryFluidHandler fluidHandler =
            new OverloadProcessingFactoryFluidHandler(inputTank, outputTank);
    private final OverloadProcessingFactoryEnergyStorage energyStorage =
            new OverloadProcessingFactoryEnergyStorage(AE2LTCommonConfig.overloadFactoryEnergyCapacity(), this::onEnergyChanged);
    private final IUpgradeInventory upgrades =
            UpgradeInventories.forMachine(ModBlocks.OVERLOAD_PROCESSING_FACTORY.get(), SPEED_CARD_SLOTS, this::onUpgradesChanged);
    private final OverloadProcessingFactoryLogic logic;
    private final FrequencyBindingHelper frequencyBinding = new FrequencyBindingHelper(this);

    private OverloadProcessingLockedRecipe lockedRecipe;
    private long consumedEnergy;
    private int processingTicksSpent;
    private boolean working;
    private boolean autoExport;
    private EnumSet<RelativeSide> allowedOutputs = EnumSet.noneOf(RelativeSide.class);
    private final AdjacentItemAutoExportHelper.DirectionalTargetCache exportTargetCache =
            new AdjacentItemAutoExportHelper.DirectionalTargetCache();
    private long lastClientUpdateTick = Long.MIN_VALUE;

    public OverloadProcessingFactoryBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.OVERLOAD_PROCESSING_FACTORY.get(), pos, blockState);
        this.logic = new OverloadProcessingFactoryLogic(this);
        getMainNode()
                .setIdlePowerUsage(0)
                .addService(IGridTickable.class, logic)
                .addService(IStorageWatcherNode.class, new IStorageWatcherNode() {
                    @Override
                    public void updateWatcher(IStackWatcher newWatcher) {
                        configureLightningWatcher(newWatcher);
                    }

                    @Override
                    public void onStackChange(AEKey what, long amount) {
                        onLightningStackChanged(what);
                    }
                });
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, OverloadProcessingFactoryBlockEntity be) {
        if (!level.isClientSide()) {
            be.frequencyBinding.serverTick();
        }
    }

    @Override
    public FrequencyBindingHelper getFrequencyBinding() {
        return frequencyBinding;
    }

    @Override
    public AENetworkedBlockEntity getFrequencyBindingBlockEntity() {
        return this;
    }

    @Override
    public void saveFrequencyBindingChanges() {
        saveChanges();
    }

    @Override
    public void markFrequencyBindingForUpdate() {
        markForUpdate();
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        frequencyBinding.onMainNodeStateChanged(reason);
    }

    public OverloadProcessingFactoryInventory getInventory() {
        return inventory;
    }

    @Override
    public IItemHandlerModifiable getMatrixInventory() {
        return inventory;
    }

    @Override
    public int getMatrixSlot() {
        return OverloadProcessingFactoryInventory.SLOT_MATRIX;
    }

    public IItemHandlerModifiable getAutomationInventory() {
        return automationInventory;
    }

    public IFluidHandler getFluidHandlerCapability(Direction side) {
        return fluidHandler;
    }

    public IEnergyStorage getEnergyStorageCapability(Direction side) {
        return energyStorage;
    }

    public OverloadProcessingFactoryEnergyStorage getEnergyStorage() {
        return energyStorage;
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return upgrades;
    }

    public FluidStack getInputFluid() {
        return inputTank.getFluid().copy();
    }

    public FluidStack getOutputFluid() {
        return outputTank.getFluid().copy();
    }

    /** GUI 流体槽交互:光标容器 → 指定 tank(0=input, 1=output)。成功返回 true。 */
    public boolean tryInsertFluidFromCarried(Player player, int tankIndex) {
        NotifyingFluidTank target = resolveTankByIndex(tankIndex);
        if (target == null) {
            return false;
        }
        boolean changed = com.moakiee.ae2lt.logic.FluidTankInteractionHelper.insertFromCarried(player, target);
        if (changed) {
            saveChanges();
        }
        return changed;
    }

    /** GUI 流体槽交互:指定 tank → 光标容器。成功返回 true。 */
    public boolean tryExtractFluidToCarried(Player player, int tankIndex) {
        NotifyingFluidTank target = resolveTankByIndex(tankIndex);
        if (target == null) {
            return false;
        }
        boolean changed = com.moakiee.ae2lt.logic.FluidTankInteractionHelper.extractToCarried(player, target);
        if (changed) {
            saveChanges();
        }
        return changed;
    }

    /** GUI 清空按钮:直接清空指定 tank。 */
    public void clearFluidTank(int tankIndex) {
        NotifyingFluidTank target = resolveTankByIndex(tankIndex);
        if (target == null || target.getFluid().isEmpty()) {
            return;
        }
        com.moakiee.ae2lt.logic.FluidTankInteractionHelper.clear(target);
        saveChanges();
    }

    private NotifyingFluidTank resolveTankByIndex(int tankIndex) {
        return switch (tankIndex) {
            case 0 -> inputTank;
            case 1 -> outputTank;
            default -> null;
        };
    }

    public int getInstalledMatrixCount() {
        return inventory.getInstalledMatrixCount();
    }

    public int getInstalledParallelCapacity() {
        return inventory.getInstalledParallelCapacity();
    }

    public Optional<OverloadProcessingRecipeCandidate> findProcessableRecipe() {
        return OverloadProcessingRecipeService.findFirstProcessable(
                getLevel(),
                inventory,
                getInputFluid(),
                getOutputFluid(),
                getAvailableHighVoltage(),
                getAvailableExtremeHighVoltage());
    }

    public long getConsumedEnergy() {
        return consumedEnergy;
    }

    public int getProcessingTicksSpent() {
        return processingTicksSpent;
    }

    public double getProgress() {
        if (lockedRecipe == null || lockedRecipe.totalEnergy() <= 0L) {
            return 0.0D;
        }
        return Math.min(1.0D, (double) consumedEnergy / (double) lockedRecipe.totalEnergy());
    }

    public void addConsumedEnergy(long amount) {
        if (addConsumedEnergyUnchecked(amount)) {
            saveChanges();
            requestClientUpdate();
        }
    }

    public void incrementProcessingTicksSpent() {
        this.processingTicksSpent++;
        saveChanges();
    }

    public void resetProgressState() {
        boolean changed = this.consumedEnergy != 0L || this.processingTicksSpent != 0;
        this.consumedEnergy = 0L;
        this.processingTicksSpent = 0;
        if (changed) {
            saveChanges();
            requestClientUpdate();
        }
    }

    public boolean hasLockedRecipe() {
        return lockedRecipe != null;
    }

    public Optional<OverloadProcessingLockedRecipe> getLockedRecipe() {
        return Optional.ofNullable(lockedRecipe);
    }

    public Optional<OverloadProcessingLockedRecipe> lockCurrentRecipe() {
        if (lockedRecipe != null) {
            return Optional.of(lockedRecipe);
        }

        Optional<OverloadProcessingRecipeCandidate> candidate = findProcessableRecipe();
        if (candidate.isEmpty()) {
            return Optional.empty();
        }

        lockedRecipe = OverloadProcessingLockedRecipe.fromCandidate(candidate.get());
        saveChanges();
        return Optional.of(lockedRecipe);
    }

    public void clearLockedRecipe() {
        if (lockedRecipe == null) {
            return;
        }
        lockedRecipe = null;
        saveChanges();
    }

    public void abortProcessing() {
        clearLockedRecipe();
        resetProgressState();
        setWorking(false);
    }

    public boolean isAutoExportEnabled() {
        return autoExport;
    }

    public void setAutoExportEnabled(boolean autoExport) {
        if (this.autoExport == autoExport) {
            return;
        }

        this.autoExport = autoExport;
        saveChanges();
        logic.onStateChanged();
    }

    public EnumSet<RelativeSide> getAllowedOutputs() {
        return allowedOutputs.isEmpty() ? EnumSet.noneOf(RelativeSide.class) : EnumSet.copyOf(allowedOutputs);
    }

    public void updateOutputSides(EnumSet<RelativeSide> allowedOutputs) {
        this.allowedOutputs = allowedOutputs.isEmpty()
                ? EnumSet.noneOf(RelativeSide.class)
                : EnumSet.copyOf(allowedOutputs);
        invalidateExportTargets();
        saveChanges();
        logic.onStateChanged();
    }

    public boolean hasAutoExportWork() {
        if (allowedOutputs.isEmpty() || !autoExport) {
            return false;
        }
        if (!outputTank.getFluid().isEmpty()) {
            return true;
        }
        return AdjacentItemAutoExportHelper.hasAnyOutput(
                autoExport,
                OverloadProcessingFactoryInventory.SLOT_OUTPUT_0,
                OverloadProcessingFactoryInventory.OUTPUT_SLOT_COUNT,
                inventory::getStackInSlot);
    }

    public boolean pushOutResult() {
        if (allowedOutputs.isEmpty() || !(level instanceof ServerLevel serverLevel)) {
            return false;
        }

        boolean pushedItem = AdjacentItemAutoExportHelper.pushOutResult(
                this,
                getOrientation(),
                allowedOutputs,
                OverloadProcessingFactoryInventory.SLOT_OUTPUT_0,
                OverloadProcessingFactoryInventory.OUTPUT_SLOT_COUNT,
                inventory::getStackInSlot,
                (slot, amount) -> inventory.extractItem(slot, amount, false),
                remainder -> {
                    if (!inventory.insertRecipeOutputs(List.of(remainder)) && !remainder.isEmpty() && level != null) {
                        Block.popResource(level, worldPosition, remainder);
                    }
                },
                direction -> getExportTarget(serverLevel, direction));

        boolean pushedFluid = AdjacentItemAutoExportHelper.pushOutFluid(
                this,
                getOrientation(),
                allowedOutputs,
                outputTank::getFluid,
                amount -> {
                    FluidStack drained = outputTank.drain(amount, FluidAction.EXECUTE);
                    return drained.getAmount();
                },
                direction -> getExportTarget(serverLevel, direction));

        return pushedItem || pushedFluid;
    }

    public void onNeighborChanged(BlockPos changedPos) {
        if (changedPos != null && worldPosition.distManhattan(changedPos) == 1) {
            invalidateExportTargets();
        }
    }

    public long getAvailableHighVoltage() {
        return simulateLightningExtract(LightningKey.HIGH_VOLTAGE, Long.MAX_VALUE);
    }

    public long getAvailableExtremeHighVoltage() {
        return simulateLightningExtract(LightningKey.EXTREME_HIGH_VOLTAGE, Long.MAX_VALUE);
    }

    public boolean completeLockedRecipe(
            OverloadProcessingLockedRecipe lockedRecipe,
            OverloadProcessingRecipeCandidate candidate) {
        if (candidate.parallel() != lockedRecipe.parallel()) {
            return false;
        }
        if (!inventory.canAcceptRecipeOutputs(candidate.recipe().value().getScaledItemResults(candidate.parallel()))) {
            return false;
        }

        for (int slot = OverloadProcessingFactoryInventory.SLOT_INPUT_0;
             slot <= OverloadProcessingFactoryInventory.SLOT_INPUT_8;
             slot++) {
            int toConsume = candidate.match().getConsumptionForSlot(slot);
            if (toConsume <= 0) {
                continue;
            }
            if (inventory.getStackInSlot(slot).getCount() < toConsume) {
                return false;
            }
        }

        FluidStack requiredInputFluid = candidate.recipe().value().fluidInput();
        int inputFluidCost = 0;
        if (!requiredInputFluid.isEmpty()) {
            long scaledInputFluidCost = (long) requiredInputFluid.getAmount() * candidate.parallel();
            if (scaledInputFluidCost > Integer.MAX_VALUE) {
                return false;
            }
            inputFluidCost = (int) scaledInputFluidCost;
        }
        if (inputFluidCost > 0) {
            FluidStack currentInput = inputTank.getFluid();
            if (currentInput.isEmpty()
                    || !FluidStack.isSameFluidSameComponents(requiredInputFluid, currentInput)
                    || currentInput.getAmount() < inputFluidCost) {
                return false;
            }
        }

        FluidStack scaledOutputFluid = candidate.recipe().value().getScaledFluidResult(candidate.parallel());
        if (!canAcceptFluidOutput(scaledOutputFluid)) {
            return false;
        }

        var lightningPlan = OverloadProcessingRecipeService.resolveLightningConsumption(
                inventory,
                lockedRecipe.lightningTier(),
                lockedRecipe.totalLightningCost(),
                getAvailableHighVoltage(),
                getAvailableExtremeHighVoltage());
        if (lightningPlan.isEmpty()) {
            return false;
        }
        var plan = lightningPlan.get();
        if (simulateLightningExtract(plan.primaryKey(), plan.primaryAmount()) < plan.primaryAmount()) {
            return false;
        }
        if (plan.hasSecondary()
                && simulateLightningExtract(plan.secondaryKey(), plan.secondaryAmount()) < plan.secondaryAmount()) {
            return false;
        }

        ItemStack[] extractedInputs = new ItemStack[OverloadProcessingFactoryInventory.INPUT_SLOT_COUNT];
        for (int slot = OverloadProcessingFactoryInventory.SLOT_INPUT_0;
             slot <= OverloadProcessingFactoryInventory.SLOT_INPUT_8;
             slot++) {
            int toConsume = candidate.match().getConsumptionForSlot(slot);
            if (toConsume <= 0) {
                continue;
            }

            ItemStack extracted = inventory.extractItem(slot, toConsume, false);
            if (extracted.getCount() != toConsume) {
                rollbackInputs(extractedInputs);
                return false;
            }
            extractedInputs[slot] = extracted;
        }

        FluidStack drainedInput = inputFluidCost <= 0
                ? FluidStack.EMPTY
                : inputTank.drain(inputFluidCost, FluidAction.EXECUTE);
        if (inputFluidCost > 0 && drainedInput.getAmount() != inputFluidCost) {
            rollbackInputs(extractedInputs);
            if (!drainedInput.isEmpty()) {
                inputTank.fill(drainedInput, FluidAction.EXECUTE);
            }
            return false;
        }

        long extractedPrimary = extractLightning(plan.primaryKey(), plan.primaryAmount());
        if (extractedPrimary < plan.primaryAmount()) {
            rollbackInputs(extractedInputs);
            if (!drainedInput.isEmpty()) {
                inputTank.fill(drainedInput, FluidAction.EXECUTE);
            }
            if (extractedPrimary > 0L) {
                insertLightning(plan.primaryKey(), extractedPrimary);
            }
            return false;
        }

        long extractedSecondary = 0L;
        if (plan.hasSecondary()) {
            extractedSecondary = extractLightning(plan.secondaryKey(), plan.secondaryAmount());
            if (extractedSecondary < plan.secondaryAmount()) {
                insertLightning(plan.primaryKey(), extractedPrimary);
                rollbackInputs(extractedInputs);
                if (!drainedInput.isEmpty()) {
                    inputTank.fill(drainedInput, FluidAction.EXECUTE);
                }
                if (extractedSecondary > 0L) {
                    insertLightning(plan.secondaryKey(), extractedSecondary);
                }
                return false;
            }
        }

        if (!inventory.insertRecipeOutputs(candidate.recipe().value().getScaledItemResults(candidate.parallel()))) {
            insertLightning(plan.primaryKey(), extractedPrimary);
            if (extractedSecondary > 0L) {
                insertLightning(plan.secondaryKey(), extractedSecondary);
            }
            if (!drainedInput.isEmpty()) {
                inputTank.fill(drainedInput, FluidAction.EXECUTE);
            }
            rollbackInputs(extractedInputs);
            return false;
        }

        int filledFluid = scaledOutputFluid.isEmpty() ? 0 : outputTank.fill(scaledOutputFluid, FluidAction.EXECUTE);
        if (!scaledOutputFluid.isEmpty() && filledFluid != scaledOutputFluid.getAmount()) {
            if (filledFluid > 0) {
                outputTank.drain(filledFluid, FluidAction.EXECUTE);
            }
            insertLightning(plan.primaryKey(), extractedPrimary);
            if (extractedSecondary > 0L) {
                insertLightning(plan.secondaryKey(), extractedSecondary);
            }
            if (!drainedInput.isEmpty()) {
                inputTank.fill(drainedInput, FluidAction.EXECUTE);
            }
            rollbackItemOutputs(candidate.recipe().value().getScaledItemResults(candidate.parallel()));
            rollbackInputs(extractedInputs);
            return false;
        }

        clearLockedRecipe();
        resetProgressState();
        setWorking(false);
        pushOutResult();
        return true;
    }

    public void openMenu(Player player, MenuHostLocator locator) {
        MenuOpener.open(OverloadProcessingFactoryMenu.TYPE, player, locator);
    }

    public boolean isWorking() {
        return working;
    }

    public void setWorking(boolean working) {
        boolean changed = this.working != working;
        this.working = working;
        if (level != null) {
            BlockState state = getBlockState();
            if (state.hasProperty(OverloadProcessingFactoryBlock.WORKING)
                    && state.getValue(OverloadProcessingFactoryBlock.WORKING) != working) {
                level.setBlock(worldPosition, state.setValue(OverloadProcessingFactoryBlock.WORKING, working), Block.UPDATE_ALL);
            } else if (changed) {
                requestClientUpdate();
            }
        }
    }

    @Override
    public void onReady() {
        super.onReady();
        frequencyBinding.onReady();
        setWorking(hasLockedRecipe());
    }

    @Override
    public void setRemoved() {
        frequencyBinding.setRemoved();
        super.setRemoved();
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        frequencyBinding.clearRemoved();
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        inventory.saveToTag(data, TAG_INVENTORY, registries);
        upgrades.writeToNBT(data, TAG_UPGRADES, registries);
        data.putLong(TAG_ENERGY, energyStorage.getStoredEnergyLong());
        data.put(TAG_INPUT_TANK, inputTank.writeToNBT(registries, new CompoundTag()));
        data.put(TAG_OUTPUT_TANK, outputTank.writeToNBT(registries, new CompoundTag()));
        data.putLong(TAG_CONSUMED_ENERGY, consumedEnergy);
        data.putInt(TAG_PROCESSING_TICKS, processingTicksSpent);
        data.putBoolean(TAG_AUTO_EXPORT, autoExport);
        ListTag outputTags = new ListTag();
        for (var side : allowedOutputs) {
            outputTags.add(StringTag.valueOf(side.name()));
        }
        data.put(TAG_ALLOWED_OUTPUTS, outputTags);
        if (lockedRecipe != null) {
            data.put(TAG_LOCKED_RECIPE, lockedRecipe.toTag(registries));
        } else {
            data.remove(TAG_LOCKED_RECIPE);
        }
        frequencyBinding.save(data);
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        inventory.loadFromTag(data, TAG_INVENTORY, registries);
        upgrades.readFromNBT(data, TAG_UPGRADES, registries);
        energyStorage.loadStoredEnergy(data.getLong(TAG_ENERGY));
        inputTank.readFromNBT(registries, data.getCompound(TAG_INPUT_TANK));
        outputTank.readFromNBT(registries, data.getCompound(TAG_OUTPUT_TANK));
        consumedEnergy = Math.max(0L, data.getLong(TAG_CONSUMED_ENERGY));
        processingTicksSpent = Math.max(0, data.getInt(TAG_PROCESSING_TICKS));
        frequencyBinding.load(data);
        autoExport = data.getBoolean(TAG_AUTO_EXPORT);
        allowedOutputs.clear();
        ListTag outputTags = data.getList(TAG_ALLOWED_OUTPUTS, Tag.TAG_STRING);
        for (int i = 0; i < outputTags.size(); i++) {
            try {
                allowedOutputs.add(RelativeSide.valueOf(outputTags.getString(i)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (data.contains(TAG_LOCKED_RECIPE, Tag.TAG_COMPOUND)) {
            lockedRecipe = OverloadProcessingLockedRecipe.fromTag(data.getCompound(TAG_LOCKED_RECIPE), registries);
        } else {
            lockedRecipe = null;
        }

        if (lockedRecipe == null) {
            consumedEnergy = 0L;
            processingTicksSpent = 0;
        } else {
            consumedEnergy = Math.min(consumedEnergy, lockedRecipe.totalEnergy());
        }
        working = lockedRecipe != null;
        invalidateExportTargets();
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                drops.add(stack.copy());
            }
        }
        for (var upgrade : upgrades) {
            if (!upgrade.isEmpty()) {
                drops.add(upgrade.copy());
            }
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        inventory.clear();
        upgrades.clear();
        inputTank.setFluid(FluidStack.EMPTY);
        outputTank.setFluid(FluidStack.EMPTY);
    }

    @Override
    public void exportSettings(appeng.util.SettingsFrom mode,
                               net.minecraft.core.component.DataComponentMap.Builder builder,
                               @org.jetbrains.annotations.Nullable Player player) {
        super.exportSettings(mode, builder, player);
        MemoryCardConfigSupport.exportAutoExportSettings(mode, builder, autoExport, allowedOutputs, tag -> {
            FrequencyBindingHelper.writeMemoryFrequency(tag, getFrequencyId());
            MemoryCardConfigSupport.writeMatrixCount(tag, this);
        });
    }

    @Override
    public void importSettings(appeng.util.SettingsFrom mode,
                               net.minecraft.core.component.DataComponentMap input,
                               @org.jetbrains.annotations.Nullable Player player) {
        super.importSettings(mode, input, player);
        MemoryCardConfigSupport.importAutoExportSettings(mode, input,
                v -> this.autoExport = v,
                sides -> this.allowedOutputs = sides,
                tag -> {
                    FrequencyBindingHelper.importMemoryFrequency(tag, this::setFrequency);
                    MemoryCardConfigSupport.restoreMatrixCount(tag, player, this);
                },
                () -> {
                    invalidateExportTargets();
                    saveChanges();
                    markForUpdate();
                });
    }

    @Override
    protected net.minecraft.world.item.Item getItemFromBlockEntity() {
        return ModBlocks.OVERLOAD_PROCESSING_FACTORY.get().asItem();
    }

    @Override
    public IGridNode getActionableNode() {
        return getMainNode().getNode();
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.allOf(Direction.class);
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.SMART;
    }

    @Override
    protected void onOrientationChanged(BlockOrientation orientation) {
        super.onOrientationChanged(orientation);
        invalidateExportTargets();
    }

    private appeng.me.storage.CompositeStorage getExportTarget(ServerLevel level, Direction direction) {
        return exportTargetCache.resolve(level, worldPosition, direction);
    }

    private void invalidateExportTargets() {
        exportTargetCache.invalidate();
    }

    @Override
    public long getMachineStoredEnergy() {
        return energyStorage.getStoredEnergyLong();
    }

    @Override
    public IEnergyStorage getMachineEnergyStorage() {
        return energyStorage;
    }

    @Override
    public int extractMachineEnergy(long amount) {
        return energyStorage.extractInternal(amount, false);
    }

    @Override
    public void onEnergyConsumed(int consumed) {
        if (consumed <= 0) {
            return;
        }
        addConsumedEnergyUnchecked(consumed);
        this.processingTicksSpent++;
        saveChanges();
        requestClientUpdate();
    }

    private boolean addConsumedEnergyUnchecked(long amount) {
        if (amount <= 0L) {
            return false;
        }
        if (amount > Long.MAX_VALUE - this.consumedEnergy) {
            this.consumedEnergy = Long.MAX_VALUE;
        } else {
            this.consumedEnergy += amount;
        }
        return true;
    }

    private boolean canAcceptFluidOutput(FluidStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        FluidStack current = outputTank.getFluid();
        if (current.isEmpty()) {
            return stack.getAmount() <= OUTPUT_TANK_CAPACITY;
        }
        return FluidStack.isSameFluidSameComponents(current, stack)
                && current.getAmount() + stack.getAmount() <= OUTPUT_TANK_CAPACITY;
    }

    private void rollbackInputs(ItemStack[] extractedInputs) {
        for (int slot = OverloadProcessingFactoryInventory.SLOT_INPUT_0;
             slot <= OverloadProcessingFactoryInventory.SLOT_INPUT_8;
             slot++) {
            ItemStack extracted = extractedInputs[slot];
            if (extracted != null && !extracted.isEmpty()) {
                inventory.insertItem(slot, extracted, false);
            }
        }
    }

    private void rollbackItemOutputs(List<ItemStack> outputs) {
        for (ItemStack output : outputs) {
            int remaining = output.getCount();
            for (int slot = OverloadProcessingFactoryInventory.SLOT_OUTPUT_0;
                 slot < OverloadProcessingFactoryInventory.SLOT_OUTPUT_0
                         + OverloadProcessingFactoryInventory.OUTPUT_SLOT_COUNT && remaining > 0;
                 slot++) {
                ItemStack current = inventory.getStackInSlot(slot);
                if (current.isEmpty() || !ItemStack.isSameItemSameComponents(current, output)) {
                    continue;
                }

                int extracted = Math.min(remaining, current.getCount());
                inventory.extractItem(slot, extracted, false);
                remaining -= extracted;
            }
        }
    }

    private long simulateLightningExtract(LightningKey key, long amount) {
        if (amount <= 0L) {
            return 0L;
        }
        var grid = getMainNode().getGrid();
        if (grid == null) {
            return 0L;
        }
        return grid.getStorageService().getInventory()
                .extract(key, amount, Actionable.SIMULATE, IActionSource.ofMachine(this));
    }

    private long extractLightning(LightningKey key, long amount) {
        if (amount <= 0L) {
            return 0L;
        }
        var grid = getMainNode().getGrid();
        if (grid == null) {
            return 0L;
        }
        return grid.getStorageService().getInventory()
                .extract(key, amount, Actionable.MODULATE, IActionSource.ofMachine(this));
    }

    private long insertLightning(LightningKey key, long amount) {
        if (amount <= 0L) {
            return 0L;
        }
        var grid = getMainNode().getGrid();
        if (grid == null) {
            return 0L;
        }
        return grid.getStorageService().getInventory()
                .insert(key, amount, Actionable.MODULATE, IActionSource.ofMachine(this));
    }

    private void onInventoryChanged() {
        saveChanges();
        requestClientUpdate();
        logic.onStateChanged();
    }

    private void onTankChanged() {
        saveChanges();
        requestClientUpdate();
        logic.onStateChanged();
    }

    private void onEnergyChanged() {
        saveChanges();
        logic.onStateChanged();
    }

    private void onUpgradesChanged() {
        saveChanges();
        logic.onStateChanged();
    }

    private void configureLightningWatcher(IStackWatcher watcher) {
        watcher.reset();
        watcher.add(LightningKey.HIGH_VOLTAGE);
        watcher.add(LightningKey.EXTREME_HIGH_VOLTAGE);
    }

    private void onLightningStackChanged(AEKey what) {
        if (LightningKey.HIGH_VOLTAGE.equals(what) || LightningKey.EXTREME_HIGH_VOLTAGE.equals(what)) {
            logic.onStateChanged();
        }
    }

    private void requestClientUpdate() {
        if (level == null) {
            markForClientUpdate();
            return;
        }

        long gameTime = level.getGameTime();
        if (lastClientUpdateTick == gameTime) {
            return;
        }

        lastClientUpdateTick = gameTime;
        markForClientUpdate();
    }
}


