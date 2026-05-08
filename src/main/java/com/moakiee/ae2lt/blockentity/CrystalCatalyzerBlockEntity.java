package com.moakiee.ae2lt.blockentity;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.mojang.serialization.Codec;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.orientation.BlockOrientation;
import appeng.api.orientation.RelativeSide;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;

import com.moakiee.ae2lt.block.CrystalCatalyzerBlock;
import com.moakiee.ae2lt.grid.FrequencyBindingHelper;
import com.moakiee.ae2lt.grid.FrequencyBindingHost;
import com.moakiee.ae2lt.logic.AdjacentItemAutoExportHelper;
import com.moakiee.ae2lt.logic.MemoryCardConfigSupport;
import com.moakiee.ae2lt.machine.common.GridRecipeMachineHost;
import com.moakiee.ae2lt.machine.crystalcatalyzer.CrystalCatalyzerAutomationInventory;
import com.moakiee.ae2lt.machine.crystalcatalyzer.CrystalCatalyzerFluidHandler;
import com.moakiee.ae2lt.machine.crystalcatalyzer.CrystalCatalyzerInventory;
import com.moakiee.ae2lt.machine.crystalcatalyzer.CrystalCatalyzerLogic;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalCatalyzerLockedRecipe;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalCatalyzerRecipeCandidate;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalCatalyzerRecipeService;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.Mode;
import com.moakiee.ae2lt.machine.overloadfactory.NotifyingFluidTank;
import com.moakiee.ae2lt.machine.overloadfactory.OverloadProcessingFactoryEnergyStorage;
import com.moakiee.ae2lt.menu.CrystalCatalyzerMenu;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModBlocks;

public class CrystalCatalyzerBlockEntity extends AENetworkedBlockEntity
        implements IActionHost, IUpgradeableObject, FrequencyBindingHost,
        GridRecipeMachineHost<CrystalCatalyzerLockedRecipe, CrystalCatalyzerRecipeCandidate> {

    private static final String TAG_INVENTORY = "Inventory";
    private static final String TAG_TANK = "Tank";
    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_CONSUMED_ENERGY = "ConsumedEnergy";
    private static final String TAG_PROCESSING_TICKS = "ProcessingTicks";
    private static final String TAG_LOCKED_RECIPE = "LockedRecipe";
    private static final String TAG_AUTO_EXPORT = "AutoExport";
    private static final String TAG_ALLOWED_OUTPUTS = "AllowedOutputs";
    private static final String TAG_MODE = "Mode";

    public static final int ENERGY_CAPACITY = 1_000_000;
    public static final int FLUID_TANK_CAPACITY_MB = 16_000;
    public static final int CYCLE_TICKS = 5;
    public static final int MATRIX_OUTPUT_MULTIPLIER = 4;
    /** 每轮固定消耗 1B (1000 mB) 水 —— 配方里已经不再带 fluid 字段,所有配方共用此常量。 */
    public static final int FIXED_FLUID_PER_CYCLE_MB = 1_000;

    public static FluidStack getFixedFluidPerCycle() {
        return new FluidStack(Fluids.WATER, FIXED_FLUID_PER_CYCLE_MB);
    }

    private Mode mode = Mode.CRYSTAL;

    private final CrystalCatalyzerInventory inventory =
            new CrystalCatalyzerInventory(this::onInventoryChanged, this::getMode);
    private final CrystalCatalyzerAutomationInventory automationInventory =
            new CrystalCatalyzerAutomationInventory(inventory);
    private final NotifyingFluidTank tank =
            new NotifyingFluidTank(FLUID_TANK_CAPACITY_MB, this::onTankChanged);
    private final CrystalCatalyzerFluidHandler fluidHandler =
            new CrystalCatalyzerFluidHandler(tank);
    private final OverloadProcessingFactoryEnergyStorage energyStorage =
            new OverloadProcessingFactoryEnergyStorage(ENERGY_CAPACITY, this::onEnergyChanged);
    private final IUpgradeInventory upgrades =
            UpgradeInventories.forMachine(ModBlocks.CRYSTAL_CATALYZER, 0, this::onUpgradesChanged);
    private final CrystalCatalyzerLogic logic;
    private final FrequencyBindingHelper frequencyBinding = new FrequencyBindingHelper(this);

    private CrystalCatalyzerLockedRecipe lockedRecipe;
    private long consumedEnergy;
    private int processingTicksSpent;
    private boolean autoExport;
    private EnumSet<RelativeSide> allowedOutputs = EnumSet.noneOf(RelativeSide.class);
    private final AdjacentItemAutoExportHelper.DirectionalTargetCache exportTargetCache =
            new AdjacentItemAutoExportHelper.DirectionalTargetCache();

    public CrystalCatalyzerBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.CRYSTAL_CATALYZER.get(), pos, blockState);
        this.logic = new CrystalCatalyzerLogic(this);
        getMainNode()
                .setIdlePowerUsage(0)
                .addService(IGridTickable.class, logic);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CrystalCatalyzerBlockEntity be) {
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

    public CrystalCatalyzerInventory getInventory() {
        return inventory;
    }

    public ResourceHandler<ItemResource> getAutomationInventory() {
        return automationInventory;
    }

    public NotifyingFluidTank getTank() {
        return tank;
    }

    public FluidStack getFluid() {
        return tank.getFluid().copy();
    }

    /** GUI 流体槽交互:光标容器 → tank。成功返回 true。 */
    public boolean tryInsertFluidFromCarried(Player player) {
        boolean changed = com.moakiee.ae2lt.logic.FluidTankInteractionHelper.insertFromCarried(player, tank);
        if (changed) {
            saveChanges();
        }
        return changed;
    }

    /** GUI 流体槽交互:tank → 光标容器。成功返回 true。 */
    public boolean tryExtractFluidToCarried(Player player) {
        boolean changed = com.moakiee.ae2lt.logic.FluidTankInteractionHelper.extractToCarried(player, tank);
        if (changed) {
            saveChanges();
        }
        return changed;
    }

    /** GUI 清空按钮:直接清空 tank(流体消失,不返还)。 */
    public void clearFluidTank() {
        if (tank.getFluid().isEmpty()) {
            return;
        }
        com.moakiee.ae2lt.logic.FluidTankInteractionHelper.clear(tank);
        saveChanges();
    }

    public ResourceHandler<FluidResource> getFluidHandlerCapability(Direction side) {
        return fluidHandler;
    }

    public EnergyHandler getEnergyStorageCapability(Direction side) {
        return energyStorage;
    }

    public OverloadProcessingFactoryEnergyStorage getEnergyStorage() {
        return energyStorage;
    }

    public CrystalCatalyzerLogic getLogic() {
        return logic;
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return upgrades;
    }

    public void openMenu(Player player, MenuHostLocator locator) {
        MenuOpener.open(CrystalCatalyzerMenu.TYPE, player, locator);
    }

    @Override
    public void onReady() {
        super.onReady();
        frequencyBinding.onReady();
        inventory.setLevel(level);
        setWorking(lockedRecipe != null);
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        inventory.setLevel(level);
        frequencyBinding.clearRemoved();
    }

    @Override
    public void setRemoved() {
        frequencyBinding.setRemoved();
        super.setRemoved();
        inventory.setLevel(null);
    }

    public Optional<CrystalCatalyzerRecipeCandidate> findProcessableRecipe() {
        if (!hasEnoughFixedFluid()) {
            return Optional.empty();
        }

        Optional<CrystalCatalyzerRecipeCandidate> candidate = CrystalCatalyzerRecipeService.findRecipe(
                level, inventory, mode);
        if (candidate.isEmpty()) {
            return Optional.empty();
        }
        return canAcceptRecipeOutput(candidate.get()) ? candidate : Optional.empty();
    }

    public Mode getMode() {
        return mode;
    }

    /**
     * 切到下一个模式。切换会立即中断当前正在进行的配方
     * （因为新模式可能根本不认现槽内催化剂）。
     */
    public void cycleMode() {
        Mode previous = this.mode;
        this.mode = previous.next();
        abortProcessing();
        saveChanges();
        markForClientUpdate();
        logic.onStateChanged();
    }

    private boolean hasEnoughFixedFluid() {
        FluidStack required = getFixedFluidPerCycle();
        FluidStack current = tank.getFluid();
        if (current.isEmpty()) {
            return false;
        }
        return FluidStack.isSameFluidSameComponents(current, required)
                && current.getAmount() >= required.getAmount();
    }

    private boolean canAcceptRecipeOutput(CrystalCatalyzerRecipeCandidate candidate) {
        return canAcceptRecipeOutput(
                candidate.recipe().value().getOutputTemplate(),
                getCurrentOutputMultiplier(candidate));
    }

    public boolean canAcceptLockedRecipeOutput(CrystalCatalyzerLockedRecipe lockedRecipe) {
        return !getLockedRecipeOutputStack(lockedRecipe).isEmpty();
    }

    private boolean canAcceptRecipeOutput(ItemStack template, int multiplier) {
        long outputCount = (long) template.getCount() * multiplier;
        if (outputCount <= 0 || outputCount > Integer.MAX_VALUE) {
            return false;
        }
        return inventory.canAcceptRecipeOutput(template.copyWithCount((int) outputCount));
    }

    private ItemStack getLockedRecipeOutputStack(CrystalCatalyzerLockedRecipe lockedRecipe) {
        ItemStack template = lockedRecipe.output();
        long outputCount = (long) template.getCount() * lockedRecipe.outputMultiplier();
        if (outputCount <= 0 || outputCount > Integer.MAX_VALUE) {
            return ItemStack.EMPTY;
        }

        ItemStack resultStack = template.copyWithCount((int) outputCount);
        return inventory.canAcceptRecipeOutput(resultStack) ? resultStack : ItemStack.EMPTY;
    }

    /**
     * Matrix-only multiplier (no parallel). Used as a fallback when we don't yet know
     * which candidate will run (e.g. restoring a locked recipe from NBT at load time).
     */
    private int getCurrentOutputMultiplier() {
        return inventory.hasLightningCollapseMatrix() ? MATRIX_OUTPUT_MULTIPLIER : 1;
    }

    /**
     * Full multiplier = parallel × matrix. Parallel is {@code amount / catalystCount},
     * where {@code amount} is the current catalyst stack size and {@code catalystCount}
     * is the per-instance input declared by the recipe. Catalyst is <em>not</em>
     * consumed, so an existing parallel factor is snapshotted at lock time via
     * {@link CrystalCatalyzerLockedRecipe#outputMultiplier()} and stays stable across
     * the whole processing cycle.
     */
    private int getCurrentOutputMultiplier(CrystalCatalyzerRecipeCandidate candidate) {
        int matrix = inventory.hasLightningCollapseMatrix() ? MATRIX_OUTPUT_MULTIPLIER : 1;
        int parallel = computeParallel(candidate);
        long multiplier = (long) Math.max(1, parallel) * matrix;
        return (int) Math.min(multiplier, Integer.MAX_VALUE);
    }

    private int computeParallel(CrystalCatalyzerRecipeCandidate candidate) {
        if (candidate == null) {
            return 1;
        }
        var recipe = candidate.recipe().value();
        int perInstance = recipe.catalystCount();
        if (perInstance <= 0) {
            return 1;
        }
        int amount = inventory.getStackInSlot(CrystalCatalyzerInventory.SLOT_CATALYST).getCount();
        return Math.max(1, amount / perInstance);
    }

    @Override
    public boolean hasLockedRecipe() {
        return lockedRecipe != null;
    }

    @Override
    public Optional<CrystalCatalyzerLockedRecipe> getLockedRecipe() {
        return Optional.ofNullable(lockedRecipe);
    }

    @Override
    public Optional<CrystalCatalyzerLockedRecipe> lockCurrentRecipe() {
        if (lockedRecipe != null) {
            return Optional.of(lockedRecipe);
        }

        Optional<CrystalCatalyzerRecipeCandidate> candidate = findProcessableRecipe();
        if (candidate.isEmpty()) {
            return Optional.empty();
        }

        lockedRecipe = CrystalCatalyzerLockedRecipe.fromCandidate(
                candidate.get(), getCurrentOutputMultiplier(candidate.get()));
        saveChanges();
        return Optional.of(lockedRecipe);
    }

    public void clearLockedRecipe() {
        boolean hadLockedRecipe = lockedRecipe != null;
        boolean hadProgress = consumedEnergy != 0L || processingTicksSpent != 0;
        if (!hadLockedRecipe && !hadProgress) {
            return;
        }
        lockedRecipe = null;
        resetProgressState();
        if (hadLockedRecipe && !hadProgress) {
            saveChanges();
        }
    }

    @Override
    public void abortProcessing() {
        clearLockedRecipe();
        setWorking(false);
    }

    @Override
    public long getConsumedEnergy() {
        return consumedEnergy;
    }

    @Override
    public int getProcessingTicksSpent() {
        return processingTicksSpent;
    }

    public double getProgress() {
        if (lockedRecipe == null || lockedRecipe.totalEnergy() <= 0L) {
            return 0.0D;
        }
        return Math.min(1.0D, (double) consumedEnergy / (double) lockedRecipe.totalEnergy());
    }

    private void addConsumedEnergy(long amount) {
        if (amount <= 0L) {
            return;
        }
        if (amount > Long.MAX_VALUE - consumedEnergy) {
            consumedEnergy = Long.MAX_VALUE;
        } else {
            consumedEnergy += amount;
        }
    }

    private void incrementProcessingTicksSpent() {
        processingTicksSpent++;
    }

    @Override
    public void resetProgressState() {
        boolean changed = consumedEnergy != 0L || processingTicksSpent != 0;
        consumedEnergy = 0L;
        processingTicksSpent = 0;
        if (changed) {
            saveChanges();
        }
    }

    @Override
    public boolean pushOutResult() {
        if (!hasAutoExportWork() || !(level instanceof ServerLevel serverLevel)) {
            return false;
        }

        return AdjacentItemAutoExportHelper.pushOutResult(
                this,
                getOrientation(),
                allowedOutputs,
                CrystalCatalyzerInventory.SLOT_OUTPUT,
                1,
                inventory::getStackInSlot,
                (slot, amount) -> inventory.extractItem(slot, amount, false),
                remainder -> {
                    ItemStack leftover = inventory.insertRecipeOutput(remainder, false);
                    if (!leftover.isEmpty() && level != null) {
                        Block.popResource(level, worldPosition, leftover);
                    }
                },
                direction -> exportTargetCache.resolve(serverLevel, worldPosition, direction));
    }

    @Override
    public boolean hasAutoExportWork() {
        return !allowedOutputs.isEmpty() && AdjacentItemAutoExportHelper.hasAnyOutput(
                autoExport,
                CrystalCatalyzerInventory.SLOT_OUTPUT,
                1,
                inventory::getStackInSlot);
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
        exportTargetCache.invalidate();
        saveChanges();
        logic.onStateChanged();
    }

    public void onNeighborChanged(@Nullable BlockPos changedPos) {
        if (changedPos == null || worldPosition.distManhattan(changedPos) == 1) {
            exportTargetCache.invalidate();
        }
    }

    @Override
    protected void onOrientationChanged(BlockOrientation orientation) {
        super.onOrientationChanged(orientation);
        exportTargetCache.invalidate();
    }

    @Override
    public boolean completeLockedRecipe(
            CrystalCatalyzerLockedRecipe lockedRecipe,
            CrystalCatalyzerRecipeCandidate candidate) {
        ItemStack resultStack = getLockedRecipeOutputStack(lockedRecipe);
        if (resultStack.isEmpty()) {
            return false;
        }

        FluidStack requiredFluid = getFixedFluidPerCycle();
        FluidStack currentFluid = tank.getFluid();
        if (currentFluid.isEmpty()
                || !FluidStack.isSameFluidSameComponents(currentFluid, requiredFluid)
                || currentFluid.getAmount() < requiredFluid.getAmount()) {
            return false;
        }

        FluidStack drained = tank.extractFluid(requiredFluid);
        if (drained.getAmount() != requiredFluid.getAmount()) {
            if (!drained.isEmpty()) {
                tank.insertFluid(drained);
            }
            return false;
        }

        ItemStack leftover = inventory.insertRecipeOutput(resultStack, false);
        if (!leftover.isEmpty()) {
            tank.insertFluid(drained);
            return false;
        }

        clearLockedRecipe();
        setWorking(false);
        pushOutResult();
        return true;
    }

    @Override
    public long getMachineStoredEnergy() {
        return energyStorage.getStoredEnergyLong();
    }

    @Override
    public EnergyHandler getMachineEnergyStorage() {
        return energyStorage;
    }

    @Override
    public int extractMachineEnergy(long amount) {
        return energyStorage.extractInternal(amount);
    }

    @Override
    public void onEnergyConsumed(int consumed) {
        addConsumedEnergy(consumed);
        incrementProcessingTicksSpent();
        saveChanges();
    }

    public boolean isWorking() {
        BlockState state = getBlockState();
        return state.hasProperty(CrystalCatalyzerBlock.WORKING)
                && state.getValue(CrystalCatalyzerBlock.WORKING);
    }

    @Override
    public void setWorking(boolean working) {
        if (level != null) {
            BlockState state = getBlockState();
            if (state.hasProperty(CrystalCatalyzerBlock.WORKING)
                    && state.getValue(CrystalCatalyzerBlock.WORKING) != working) {
                level.setBlock(worldPosition, state.setValue(CrystalCatalyzerBlock.WORKING, working), Block.UPDATE_ALL);
            }
        }
    }

    @Override
    public void saveAdditional(ValueOutput data) {
        super.saveAdditional(data);
        inventory.saveToTag(data, TAG_INVENTORY);
        tank.serialize(data.child(TAG_TANK));
        data.putLong(TAG_ENERGY, energyStorage.getStoredEnergyLong());
        data.putLong(TAG_CONSUMED_ENERGY, consumedEnergy);
        data.putInt(TAG_PROCESSING_TICKS, processingTicksSpent);
        data.putBoolean(TAG_AUTO_EXPORT, autoExport);
        var outputTags = data.list(TAG_ALLOWED_OUTPUTS, Codec.STRING);
        for (var side : allowedOutputs) {
            outputTags.add(side.name());
        }
        data.putString(TAG_MODE, mode.getSerializedName());
        if (lockedRecipe != null) {
            lockedRecipe.writeTo(data.child(TAG_LOCKED_RECIPE));
        } else {
            data.discard(TAG_LOCKED_RECIPE);
        }
        frequencyBinding.save(data);
    }

    @Override
    public void loadTag(ValueInput data) {
        super.loadTag(data);
        inventory.loadFromTag(data, TAG_INVENTORY);
        tank.deserialize(data.childOrEmpty(TAG_TANK));
        energyStorage.loadStoredEnergy(data.getLongOr(TAG_ENERGY, 0L));
        consumedEnergy = Math.max(0L, data.getLongOr(TAG_CONSUMED_ENERGY, 0L));
        processingTicksSpent = Math.max(0, data.getIntOr(TAG_PROCESSING_TICKS, 0));
        frequencyBinding.load(data);
        autoExport = data.getBooleanOr(TAG_AUTO_EXPORT, false);
        allowedOutputs.clear();
        for (String sideName : data.listOrEmpty(TAG_ALLOWED_OUTPUTS, Codec.STRING)) {
            try {
                allowedOutputs.add(RelativeSide.valueOf(sideName));
            } catch (IllegalArgumentException ignored) {
            }
        }
        String modeName = data.getStringOr(TAG_MODE, Mode.CRYSTAL.getSerializedName());
        mode = Mode.CRYSTAL;
        for (Mode m : Mode.values()) {
            if (m.getSerializedName().equals(modeName)) {
                mode = m;
                break;
            }
        }
        lockedRecipe = data.child(TAG_LOCKED_RECIPE)
                .map(recipeData -> CrystalCatalyzerLockedRecipe.fromInput(recipeData, getCurrentOutputMultiplier()))
                .orElse(null);
        if (lockedRecipe == null) {
            consumedEnergy = 0L;
            processingTicksSpent = 0;
        } else {
            consumedEnergy = Math.min(consumedEnergy, lockedRecipe.totalEnergy());
        }
        exportTargetCache.invalidate();
    }

    @Override
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        for (int slot = CrystalCatalyzerInventory.SLOT_CATALYST;
             slot <= CrystalCatalyzerInventory.SLOT_MATRIX;
             slot++) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(data, inventory.getStackInSlot(slot));
        }
    }

    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        for (int slot = CrystalCatalyzerInventory.SLOT_CATALYST;
             slot <= CrystalCatalyzerInventory.SLOT_MATRIX;
             slot++) {
            ItemStack oldStack = inventory.getStackInSlot(slot);
            ItemStack newStack = ItemStack.OPTIONAL_STREAM_CODEC.decode(data);
            if (!ItemStack.matches(oldStack, newStack)) {
                inventory.setClientRenderStack(slot, newStack);
                changed = true;
            }
        }
        return changed;
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
    }

    @Override
    public void clearContent() {
        super.clearContent();
        abortProcessing();
        inventory.clear();
        tank.setFluid(FluidStack.EMPTY);
        energyStorage.loadStoredEnergy(0L);
        autoExport = false;
        allowedOutputs.clear();
        exportTargetCache.invalidate();
        saveChanges();
        markForClientUpdate();
        logic.onStateChanged();
    }

    @Override
    public void exportSettings(appeng.util.SettingsFrom mode,
                               net.minecraft.core.component.DataComponentMap.Builder builder,
                               @org.jetbrains.annotations.Nullable Player player) {
        super.exportSettings(mode, builder, player);
        MemoryCardConfigSupport.exportAutoExportSettings(mode, builder, autoExport, allowedOutputs,
                tag -> FrequencyBindingHelper.writeMemoryFrequency(tag, getFrequencyId()));
    }

    @Override
    public void importSettings(appeng.util.SettingsFrom mode,
                               net.minecraft.core.component.DataComponentMap input,
                               @org.jetbrains.annotations.Nullable Player player) {
        super.importSettings(mode, input, player);
        MemoryCardConfigSupport.importAutoExportSettings(mode, input,
                v -> this.autoExport = v,
                sides -> this.allowedOutputs = sides,
                tag -> FrequencyBindingHelper.importMemoryFrequency(tag, this::setFrequency),
                () -> {
                    exportTargetCache.invalidate();
                    saveChanges();
                    markForClientUpdate();
                });
    }

    @Override
    protected net.minecraft.world.item.Item getItemFromBlockEntity() {
        return ModBlocks.CRYSTAL_CATALYZER.get().asItem();
    }

    @Override
    public IGridNode getActionableNode() {
        return getMainNode().getNode();
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.allOf(Direction.class);
    }

    private void onInventoryChanged() {
        saveChanges();
        markForClientUpdate();
        logic.onStateChanged();
    }

    private void onTankChanged() {
        saveChanges();
        markForClientUpdate();
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
}
