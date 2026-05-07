package com.moakiee.ae2lt.blockentity;

import java.util.List;
import java.util.Optional;
import java.util.EnumSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandlerModifiable;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IStackWatcher;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageWatcherNode;
import appeng.api.orientation.RelativeSide;
import appeng.api.stacks.AEKey;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.orientation.BlockOrientation;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;

import com.moakiee.ae2lt.block.LightningAssemblyChamberBlock;
import com.moakiee.ae2lt.grid.FrequencyBindingHelper;
import com.moakiee.ae2lt.grid.FrequencyBindingHost;
import com.moakiee.ae2lt.logic.AdjacentItemAutoExportHelper;
import com.moakiee.ae2lt.logic.MemoryCardConfigSupport;
import com.moakiee.ae2lt.machine.common.GridRecipeMachineHost;
import com.moakiee.ae2lt.machine.common.SingleOutputLightningRecipeExecutor;
import com.moakiee.ae2lt.machine.lightningassembly.LightningAssemblyChamberAutomationInventory;
import com.moakiee.ae2lt.machine.lightningassembly.LightningAssemblyChamberEnergyStorage;
import com.moakiee.ae2lt.machine.lightningassembly.LightningAssemblyChamberInventory;
import com.moakiee.ae2lt.machine.lightningassembly.LightningAssemblyChamberLogic;
import com.moakiee.ae2lt.menu.LightningAssemblyChamberMenu;
import com.moakiee.ae2lt.machine.lightningassembly.recipe.LightningAssemblyLockedRecipe;
import com.moakiee.ae2lt.machine.lightningassembly.recipe.LightningAssemblyRecipeCandidate;
import com.moakiee.ae2lt.machine.lightningassembly.recipe.LightningAssemblyRecipeService;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModBlocks;

public class LightningAssemblyChamberBlockEntity extends AENetworkedBlockEntity
    implements IUpgradeableObject, FrequencyBindingHost,
        GridRecipeMachineHost<LightningAssemblyLockedRecipe, LightningAssemblyRecipeCandidate> {
    private static final String TAG_INVENTORY = "Inventory";
    private static final String TAG_LOCKED_RECIPE = "LockedRecipe";
    private static final String TAG_UPGRADES = "Upgrades";
    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_CONSUMED_ENERGY = "ConsumedEnergy";
    private static final String TAG_PROCESSING_TICKS = "ProcessingTicks";
    private static final String TAG_AUTO_EXPORT = "AutoExport";
    private static final String TAG_ALLOWED_OUTPUTS = "AllowedOutputs";

    public static final int ENERGY_CAPACITY = 1_000_000;
    public static final int SPEED_CARD_SLOTS = 4;

    private final LightningAssemblyChamberInventory inventory =
            new LightningAssemblyChamberInventory(this::onInventoryChanged);
    private final LightningAssemblyChamberAutomationInventory automationInventory =
            new LightningAssemblyChamberAutomationInventory(inventory);
    private final LightningAssemblyChamberEnergyStorage energyStorage =
            new LightningAssemblyChamberEnergyStorage(ENERGY_CAPACITY, this::onEnergyChanged);
    private final IUpgradeInventory upgrades =
            UpgradeInventories.forMachine(ModBlocks.LIGHTNING_ASSEMBLY_CHAMBER.get(), SPEED_CARD_SLOTS, this::onUpgradesChanged);
    private final LightningAssemblyChamberLogic logic;
    private final FrequencyBindingHelper frequencyBinding = new FrequencyBindingHelper(this);
    private LightningAssemblyLockedRecipe lockedRecipe;
    private long consumedEnergy;
    private int processingTicksSpent;
    private boolean working;
    private boolean powered;
    private boolean autoExport;
    private ItemStack clientRecipeResult = ItemStack.EMPTY;
    private EnumSet<RelativeSide> allowedOutputs = EnumSet.noneOf(RelativeSide.class);
    private final AdjacentItemAutoExportHelper.DirectionalTargetCache exportTargetCache =
            new AdjacentItemAutoExportHelper.DirectionalTargetCache();

    public LightningAssemblyChamberBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.LIGHTNING_ASSEMBLY_CHAMBER.get(), pos, blockState);
        this.logic = new LightningAssemblyChamberLogic(this);
        this.getMainNode()
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

    public static void serverTick(Level level, BlockPos pos, BlockState state, LightningAssemblyChamberBlockEntity be) {
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

    public LightningAssemblyChamberInventory getInventory() {
        return inventory;
    }

    public IItemHandlerModifiable getAutomationInventory() {
        return automationInventory;
    }

    public IEnergyStorage getEnergyStorageCapability(Direction side) {
        return energyStorage;
    }

    public LightningAssemblyChamberEnergyStorage getEnergyStorage() {
        return energyStorage;
    }

    public LightningAssemblyChamberLogic getLogic() {
        return logic;
    }

    public Optional<LightningAssemblyRecipeCandidate> findProcessableRecipe() {
        return LightningAssemblyRecipeService.findFirstProcessable(
                getLevel(),
                inventory,
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
        if (lockedRecipe == null || lockedRecipe.totalEnergy() <= 0) {
            return 0.0D;
        }

        return Math.min(1.0D, (double) consumedEnergy / (double) lockedRecipe.totalEnergy());
    }

    public void addConsumedEnergy(long amount) {
        if (amount <= 0) {
            return;
        }

        if (amount > Long.MAX_VALUE - this.consumedEnergy) {
            this.consumedEnergy = Long.MAX_VALUE;
        } else {
            this.consumedEnergy += amount;
        }
        saveChanges();
        markForClientUpdate();
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
            markForClientUpdate();
        }
    }

    public boolean hasLockedRecipe() {
        return lockedRecipe != null;
    }

    public Optional<LightningAssemblyLockedRecipe> getLockedRecipe() {
        return Optional.ofNullable(lockedRecipe);
    }

    public Optional<LightningAssemblyLockedRecipe> lockCurrentRecipe() {
        if (lockedRecipe != null) {
            return Optional.of(lockedRecipe);
        }

        Optional<LightningAssemblyRecipeCandidate> candidate = findProcessableRecipe();
        if (candidate.isEmpty()) {
            return Optional.empty();
        }

        lockedRecipe = LightningAssemblyLockedRecipe.fromCandidate(candidate.get());
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
        return !allowedOutputs.isEmpty() && AdjacentItemAutoExportHelper.hasAnyOutput(
                autoExport,
                LightningAssemblyChamberInventory.SLOT_OUTPUT,
                1,
                inventory::getStackInSlot);
    }

    public boolean pushOutResult() {
        if (allowedOutputs.isEmpty() || !hasAutoExportWork() || !(level instanceof ServerLevel serverLevel)) {
            return false;
        }

        return AdjacentItemAutoExportHelper.pushOutResult(
                this,
                getOrientation(),
                allowedOutputs,
                LightningAssemblyChamberInventory.SLOT_OUTPUT,
                1,
                inventory::getStackInSlot,
                (slot, amount) -> inventory.extractItem(slot, amount, false),
                remainder -> {
                    ItemStack leftover = inventory.insertRecipeOutput(remainder, false);
                    if (!leftover.isEmpty() && level != null) {
                        Block.popResource(level, worldPosition, leftover);
                    }
                },
                direction -> getExportTarget(serverLevel, direction));
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

    public boolean hasLightningCollapseMatrix() {
        return inventory.hasLightningCollapseMatrix();
    }

    public boolean completeLockedRecipe(
            LightningAssemblyLockedRecipe lockedRecipe,
            LightningAssemblyRecipeCandidate candidate) {
        boolean completed = SingleOutputLightningRecipeExecutor.complete(
                LightningAssemblyChamberInventory.SLOT_INPUT_0,
                LightningAssemblyChamberInventory.SLOT_INPUT_8,
                candidate.match()::getConsumptionForSlot,
                candidate.recipe().value().getResultStack(),
                () -> LightningAssemblyRecipeService.resolveLightningConsumption(
                                inventory,
                                lockedRecipe.lightningTier(),
                                lockedRecipe.lightningCost(),
                                getAvailableHighVoltage(),
                                getAvailableExtremeHighVoltage())
                        .map(plan -> new SingleOutputLightningRecipeExecutor.LightningPlan(plan.key(), plan.amount())),
                new SingleOutputLightningRecipeExecutor.InventoryAdapter() {
                    @Override
                    public boolean canAcceptOutput(ItemStack result) {
                        return inventory.canAcceptRecipeOutput(result);
                    }

                    @Override
                    public ItemStack getStackInSlot(int slot) {
                        return inventory.getStackInSlot(slot);
                    }

                    @Override
                    public ItemStack extractItem(int slot, int amount) {
                        return inventory.extractItem(slot, amount, false);
                    }

                    @Override
                    public ItemStack insertOutput(ItemStack stack) {
                        return inventory.insertRecipeOutput(stack, false);
                    }

                    @Override
                    public void insertInput(int slot, ItemStack stack) {
                        inventory.insertItem(slot, stack, false);
                    }
                },
                new SingleOutputLightningRecipeExecutor.LightningAdapter() {
                    @Override
                    public long simulateExtract(LightningKey key, long amount) {
                        return simulateLightningExtract(key, amount);
                    }

                    @Override
                    public long extract(LightningKey key, long amount) {
                        return extractLightning(key, amount);
                    }

                    @Override
                    public long insert(LightningKey key, long amount) {
                        return insertLightning(key, amount);
                    }
                });
        if (!completed) {
            return false;
        }

        clearLockedRecipe();
        resetProgressState();
        setWorking(false);
        pushOutResult();
        return true;
    }

    public void openMenu(Player player, MenuHostLocator locator) {
        MenuOpener.open(LightningAssemblyChamberMenu.TYPE, player, locator);
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return upgrades;
    }

    public boolean isWorking() {
        return working;
    }

    public boolean isPowered() {
        return powered;
    }

    public ItemStack getClientRecipeResult() {
        return clientRecipeResult;
    }

    public void setWorking(boolean working) {
        boolean changed = this.working != working;
        this.working = working;
        if (level != null) {
            BlockState state = getBlockState();
            if (state.hasProperty(LightningAssemblyChamberBlock.WORKING)
                    && state.getValue(LightningAssemblyChamberBlock.WORKING) != working) {
                level.setBlock(worldPosition, state.setValue(LightningAssemblyChamberBlock.WORKING, working), Block.UPDATE_ALL);
            } else if (changed) {
                markForClientUpdate();
            }
        }
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        frequencyBinding.onMainNodeStateChanged(reason);
        if (reason != IGridNodeListener.State.GRID_BOOT) {
            refreshPoweredState();
        }
    }

    private void refreshPoweredState() {
        boolean newState = false;
        var grid = getMainNode().getGrid();
        if (grid != null
                && getMainNode().isPowered()
                && grid.getEnergyService().extractAEPower(1, Actionable.SIMULATE, PowerMultiplier.CONFIG) > 0.0001
                && energyStorage.getStoredEnergyLong() > 0L) {
            newState = true;
        }

        if (newState != this.powered) {
            this.powered = newState;
            markForUpdate();
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

    private void onInventoryChanged() {
        saveChanges();
        markForClientUpdate();
        logic.onStateChanged();
    }

    private void onEnergyChanged() {
        saveChanges();
        logic.onStateChanged();
        refreshPoweredState();
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

    private appeng.me.storage.CompositeStorage getExportTarget(ServerLevel level, Direction direction) {
        return exportTargetCache.resolve(level, worldPosition, direction);
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        inventory.saveToTag(data, TAG_INVENTORY, registries);
        upgrades.writeToNBT(data, TAG_UPGRADES, registries);
        data.putLong(TAG_ENERGY, energyStorage.getStoredEnergyLong());
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
            lockedRecipe = LightningAssemblyLockedRecipe.fromTag(data.getCompound(TAG_LOCKED_RECIPE), registries);
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
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        for (int slot = LightningAssemblyChamberInventory.SLOT_INPUT_0;
             slot <= LightningAssemblyChamberInventory.SLOT_INPUT_8;
             slot++) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(data, inventory.getStackInSlot(slot));
        }
        ItemStack.OPTIONAL_STREAM_CODEC.encode(data,
                lockedRecipe != null ? lockedRecipe.result() : ItemStack.EMPTY);
    }

    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        for (int slot = LightningAssemblyChamberInventory.SLOT_INPUT_0;
             slot <= LightningAssemblyChamberInventory.SLOT_INPUT_8;
             slot++) {
            ItemStack oldStack = inventory.getStackInSlot(slot);
            ItemStack newStack = ItemStack.OPTIONAL_STREAM_CODEC.decode(data);
            if (!ItemStack.matches(oldStack, newStack)) {
                inventory.setClientRenderStack(slot, newStack);
                changed = true;
            }
        }
        ItemStack newResult = ItemStack.OPTIONAL_STREAM_CODEC.decode(data);
        if (!ItemStack.matches(clientRecipeResult, newResult)) {
            clientRecipeResult = newResult;
            changed = true;
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
                    invalidateExportTargets();
                    saveChanges();
                    markForUpdate();
                });
    }

    @Override
    protected net.minecraft.world.item.Item getItemFromBlockEntity() {
        return ModBlocks.LIGHTNING_ASSEMBLY_CHAMBER.get().asItem();
    }

    @Override
    public IGridNode getActionableNode() {
        return getMainNode().getNode();
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.allOf(Direction.class);
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
        addConsumedEnergy(consumed);
        incrementProcessingTicksSpent();
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
}



