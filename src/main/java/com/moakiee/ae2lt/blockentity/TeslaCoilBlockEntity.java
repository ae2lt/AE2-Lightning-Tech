package com.moakiee.ae2lt.blockentity;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.orientation.BlockOrientation;
import appeng.api.orientation.RelativeSide;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;

import com.moakiee.ae2lt.block.TeslaCoilBlock;
import com.moakiee.ae2lt.grid.FrequencyBindingHelper;
import com.moakiee.ae2lt.grid.FrequencyBindingHost;
import com.moakiee.ae2lt.grid.OverloadedGridNodeOwner;
import com.moakiee.ae2lt.machine.teslacoil.TeslaCoilAutomationInventory;
import com.moakiee.ae2lt.machine.teslacoil.TeslaCoilEnergyStorage;
import com.moakiee.ae2lt.machine.teslacoil.TeslaCoilInventory;
import com.moakiee.ae2lt.machine.teslacoil.TeslaCoilLogic;
import com.moakiee.ae2lt.machine.teslacoil.TeslaCoilMode;
import com.moakiee.ae2lt.machine.teslacoil.TeslaCoilStatus;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.menu.TeslaCoilMenu;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

public class TeslaCoilBlockEntity extends AENetworkedBlockEntity
        implements IActionHost, FrequencyBindingHost, OverloadedGridNodeOwner {
    public static final int ENERGY_CAPACITY = 16_000_000;
    private static final String TAG_INVENTORY = "Inventory";
    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_CONSUMED_ENERGY = "ConsumedEnergy";
    private static final String TAG_PROCESSING_TICKS = "ProcessingTicks";
    private static final String TAG_SELECTED_MODE = "SelectedMode";
    private static final String TAG_LOCKED_MODE = "LockedMode";
    private static final String TAG_LOCKED_BATCH_SIZE = "LockedBatchSize";

    private final TeslaCoilInventory inventory = new TeslaCoilInventory(this::onInventoryChanged);
    private final TeslaCoilAutomationInventory automationInventory = new TeslaCoilAutomationInventory(inventory);
    private final TeslaCoilEnergyStorage energyStorage = new TeslaCoilEnergyStorage(
            ENERGY_CAPACITY,
            this::onEnergyChanged);
    private final TeslaCoilLogic logic;
    private final FrequencyBindingHelper frequencyBinding = new FrequencyBindingHelper(this);

    private TeslaCoilMode selectedMode = TeslaCoilMode.HIGH_VOLTAGE;
    private TeslaCoilMode lockedMode;
    private long lockedBatchSize = 0L;
    private long consumedEnergy;
    private int processingTicksSpent;
    private boolean working;

    public TeslaCoilBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.TESLA_COIL.get(), pos, blockState);
        this.logic = new TeslaCoilLogic(this);
        getMainNode()
                .setIdlePowerUsage(0)
                .addService(IGridTickable.class, logic);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TeslaCoilBlockEntity be) {
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

    public TeslaCoilInventory getInventory() {
        return inventory;
    }

    public IItemHandlerModifiable getAutomationInventory() {
        return automationInventory;
    }

    public IEnergyStorage getEnergyStorageCapability(Direction side) {
        return energyStorage;
    }

    public TeslaCoilEnergyStorage getEnergyStorage() {
        return energyStorage;
    }

    public TeslaCoilLogic getLogic() {
        return logic;
    }

    public TeslaCoilMode getSelectedMode() {
        return selectedMode;
    }

    public long getCurrentTotalEnergy() {
        TeslaCoilMode mode = lockedMode != null ? lockedMode : selectedMode;
        long batchSize = lockedMode != null ? lockedBatchSize : getBatchSizeForMode(mode);
        return getTotalEnergyFor(mode, batchSize);
    }

    public void cycleMode() {
        selectedMode = selectedMode.next();
        saveChanges();
        markForClientUpdate();
        logic.onStateChanged();
    }

    public boolean hasLockedMode() {
        return lockedMode != null;
    }

    public boolean lockSelectedMode() {
        if (lockedMode != null || !canStartSelectedMode()) {
            return false;
        }

        lockedMode = selectedMode;
        lockedBatchSize = getBatchSizeForMode(selectedMode);
        if (lockedBatchSize <= 0L) {
            lockedMode = null;
            return false;
        }
        saveChanges();
        markForClientUpdate();
        logic.onStateChanged();
        return true;
    }

    public boolean hasLocalStartPrerequisites() {
        return hasLocalPrerequisites(selectedMode, getBatchSizeForMode(selectedMode));
    }

    /**
     * 仅检查本地槽位是否具备开始一次操作的最小资源 —— 即 HV 模式至少 1 份粉、
     * EHV 模式装了矩阵 —— 不考虑 ME 网络能否接收输出 / 提供 HV 输入。
     *
     * <p>用于决定能否安全 SLEEP：本地资源耗尽时 sleep 会通过
     * {@link #onInventoryChanged()} 在补给到位时重新 alertDevice 唤醒；
     * 但"本地够、网络满"这种情况不能 sleep —— 网络存量变化不会回调到本设备,
     * 一旦 sleep 就会卡死直到玩家手动碰库存。</p>
     */
    public boolean hasLocalResourcesForMinimumOperation() {
        return switch (selectedMode) {
            case HIGH_VOLTAGE -> {
                int dustPerOp = selectedMode.requiredDust();
                yield dustPerOp > 0
                        && inventory.getStackInSlot(TeslaCoilInventory.SLOT_DUST).getCount() >= dustPerOp;
            }
            case EXTREME_HIGH_VOLTAGE -> inventory.hasMatrix();
        };
    }

    public boolean hasLockedModeLocalPrerequisites() {
        return lockedMode != null && hasLocalPrerequisites(lockedMode, lockedBatchSize);
    }

    public boolean canStartSelectedMode() {
        long batchSize = getBatchSizeForMode(selectedMode);
        return batchSize > 0L
                && hasLocalPrerequisites(selectedMode, batchSize)
                && canCommitAgainstNetwork(selectedMode, batchSize);
    }

    public boolean hasEnoughEnergyForSelectedStart() {
        long batchSize = getBatchSizeForMode(selectedMode);
        return batchSize > 0L
                && energyStorage.getStoredEnergyLong() >= selectedMode.requiredEnergyForTick(
                        0,
                        0L,
                        getTotalEnergyFor(selectedMode, batchSize));
    }

    public long getConsumedEnergy() {
        return consumedEnergy;
    }

    public int getProcessingTicksSpent() {
        return processingTicksSpent;
    }

    public long getRequiredEnergyForNextTick() {
        if (lockedMode == null) {
            return 0L;
        }
        return lockedMode.requiredEnergyForTick(processingTicksSpent, consumedEnergy, getLockedTotalEnergy());
    }

    public double getProgress() {
        long totalEnergy = getLockedTotalEnergy();
        if (lockedMode == null || totalEnergy <= 0L) {
            return 0.0D;
        }
        return Math.min(1.0D, (double) consumedEnergy / (double) totalEnergy);
    }

    public boolean isReadyToCommit() {
        long totalEnergy = getLockedTotalEnergy();
        return lockedMode != null
                && lockedBatchSize > 0L
                && processingTicksSpent >= TeslaCoilMode.PROCESS_TICKS
                && consumedEnergy >= totalEnergy;
    }

    public void advanceProgress(long amount) {
        if (lockedMode == null || amount <= 0L) {
            return;
        }

        consumedEnergy = Math.min(getLockedTotalEnergy(), consumedEnergy + amount);
        processingTicksSpent = Math.min(TeslaCoilMode.PROCESS_TICKS, processingTicksSpent + 1);
        saveChanges();
        markForClientUpdate();
    }

    public TeslaCoilStatus getStatus() {
        if (lockedMode == null) {
            return TeslaCoilStatus.IDLE;
        }

        if (isReadyToCommit()) {
            if (!hasLocalPrerequisites(lockedMode, lockedBatchSize)) {
                return TeslaCoilStatus.WAITING_INPUTS;
            }
            return canCommitAgainstNetwork(lockedMode, lockedBatchSize)
                    ? TeslaCoilStatus.READY
                    : TeslaCoilStatus.WAITING_NETWORK;
        }

        if (!hasLocalPrerequisites(lockedMode, lockedBatchSize)) {
            return TeslaCoilStatus.WAITING_INPUTS;
        }

        long required = getRequiredEnergyForNextTick();
        if (required <= 0L) {
            return TeslaCoilStatus.READY;
        }

        return energyStorage.getStoredEnergyLong() >= required
                ? TeslaCoilStatus.CHARGING
                : TeslaCoilStatus.WAITING_FE;
    }

    public long getAvailableHighVoltage() {
        return getAvailableLightning(LightningKey.HIGH_VOLTAGE);
    }

    public long getAvailableExtremeHighVoltage() {
        return getAvailableLightning(LightningKey.EXTREME_HIGH_VOLTAGE);
    }

    public boolean isMatrixInstalled() {
        return inventory.hasMatrix();
    }

    public boolean commitLockedMode() {
        if (lockedMode == null
                || lockedBatchSize <= 0L
                || !canCommitAgainstNetwork(lockedMode, lockedBatchSize)
                || !hasLocalPrerequisites(lockedMode, lockedBatchSize)) {
            return false;
        }

        boolean committed = switch (lockedMode) {
            case HIGH_VOLTAGE -> commitHighVoltage();
            case EXTREME_HIGH_VOLTAGE -> commitExtremeHighVoltage();
        };
        if (!committed) {
            return false;
        }

        lockedMode = null;
        lockedBatchSize = 0L;
        consumedEnergy = 0L;
        processingTicksSpent = 0;
        saveChanges();
        markForClientUpdate();
        logic.onStateChanged();
        setWorking(false);
        return true;
    }

    public void openMenu(Player player, MenuHostLocator locator) {
        MenuOpener.open(TeslaCoilMenu.TYPE, player, locator);
    }

    public boolean isWorking() {
        return working;
    }

    public void setWorking(boolean working) {
        boolean changed = this.working != working;
        this.working = working;
        if (level != null) {
            BlockState state = getBlockState();
            if (state.hasProperty(TeslaCoilBlock.WORKING)
                    && state.getValue(TeslaCoilBlock.WORKING) != working) {
                level.setBlock(worldPosition, state.setValue(TeslaCoilBlock.WORKING, working), Block.UPDATE_ALL);
            } else if (changed) {
                markForClientUpdate();
            }
        }
    }

    @Override
    public void onReady() {
        super.onReady();
        frequencyBinding.onReady();
        setWorking(hasLockedMode());
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
        data.putLong(TAG_ENERGY, energyStorage.getStoredEnergyLong());
        data.putLong(TAG_CONSUMED_ENERGY, consumedEnergy);
        data.putInt(TAG_PROCESSING_TICKS, processingTicksSpent);
        data.putString(TAG_SELECTED_MODE, selectedMode.getSerializedName());
        if (lockedMode != null) {
            data.putString(TAG_LOCKED_MODE, lockedMode.getSerializedName());
            data.putLong(TAG_LOCKED_BATCH_SIZE, lockedBatchSize);
        } else {
            data.remove(TAG_LOCKED_MODE);
            data.remove(TAG_LOCKED_BATCH_SIZE);
        }
        frequencyBinding.save(data);
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        inventory.loadFromTag(data, TAG_INVENTORY, registries);
        energyStorage.loadStoredEnergy(data.getLong(TAG_ENERGY));
        selectedMode = TeslaCoilMode.fromName(data.getString(TAG_SELECTED_MODE));
        lockedMode = data.contains(TAG_LOCKED_MODE)
                ? TeslaCoilMode.fromName(data.getString(TAG_LOCKED_MODE))
                : null;
        lockedBatchSize = Math.max(0L, data.getLong(TAG_LOCKED_BATCH_SIZE));
        consumedEnergy = Math.max(0L, data.getLong(TAG_CONSUMED_ENERGY));
        processingTicksSpent = Math.max(0, data.getInt(TAG_PROCESSING_TICKS));
        frequencyBinding.load(data);

        if (lockedMode == null) {
            lockedBatchSize = 0L;
            consumedEnergy = 0L;
            processingTicksSpent = 0;
        } else {
            if (lockedBatchSize <= 0L) {
                lockedBatchSize = 1L;
            }
            consumedEnergy = Math.min(consumedEnergy, getLockedTotalEnergy());
            processingTicksSpent = Math.min(processingTicksSpent, TeslaCoilMode.PROCESS_TICKS);
        }
        working = lockedMode != null;
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
        inventory.clear();
    }

    @Override
    public void exportSettings(appeng.util.SettingsFrom mode,
                               net.minecraft.core.component.DataComponentMap.Builder builder,
                               @org.jetbrains.annotations.Nullable Player player) {
        super.exportSettings(mode, builder, player);
        com.moakiee.ae2lt.logic.MemoryCardConfigSupport.exportMemoryCardSettings(mode, builder, tag -> {
            com.moakiee.ae2lt.logic.MemoryCardConfigSupport.writeEnum(tag, TAG_SELECTED_MODE, selectedMode);
            FrequencyBindingHelper.writeMemoryFrequency(tag, getFrequencyId());
        });
    }

    @Override
    public void importSettings(appeng.util.SettingsFrom mode,
                               net.minecraft.core.component.DataComponentMap input,
                               @org.jetbrains.annotations.Nullable Player player) {
        super.importSettings(mode, input, player);
        com.moakiee.ae2lt.logic.MemoryCardConfigSupport.importMemoryCardSettings(mode, input, tag -> {
            var mode2 = com.moakiee.ae2lt.logic.MemoryCardConfigSupport.readEnum(
                    tag, TAG_SELECTED_MODE, TeslaCoilMode.class, selectedMode);
            this.selectedMode = mode2;
            FrequencyBindingHelper.importMemoryFrequency(tag, this::setFrequency);
            saveChanges();
            markForUpdate();
        });
    }

    @Override
    protected net.minecraft.world.item.Item getItemFromBlockEntity() {
        return ModBlocks.TESLA_COIL.get().asItem();
    }

    @Override
    public IGridNode getActionableNode() {
        return getMainNode().getNode();
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.of(orientation.getSide(RelativeSide.BOTTOM));
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.SMART;
    }

    private boolean hasLocalPrerequisites(TeslaCoilMode mode, long batchSize) {
        if (batchSize <= 0L) {
            return false;
        }

        return switch (mode) {
            case HIGH_VOLTAGE -> inventory.hasRequiredDust(getRequiredDustForBatch(mode, batchSize));
            case EXTREME_HIGH_VOLTAGE -> inventory.hasMatrix();
        };
    }

    private boolean canCommitAgainstNetwork(TeslaCoilMode mode, long batchSize) {
        if (batchSize <= 0L || simulateInsert(mode.outputKey(), batchSize) < batchSize) {
            return false;
        }

        if (mode == TeslaCoilMode.EXTREME_HIGH_VOLTAGE) {
            long requiredHighVoltage = getRequiredHighVoltageForBatch(mode, batchSize);
            return simulateExtract(LightningKey.HIGH_VOLTAGE, requiredHighVoltage) >= requiredHighVoltage;
        }

        return true;
    }

    private long getAvailableLightning(LightningKey key) {
        return simulateExtract(key, Long.MAX_VALUE);
    }

    private boolean commitHighVoltage() {
        int requiredDust = getRequiredDustForBatch(lockedMode, lockedBatchSize);
        ItemStack extractedDust = inventory.extractItem(TeslaCoilInventory.SLOT_DUST, requiredDust, false);
        if (extractedDust.getCount() != requiredDust) {
            return false;
        }

        long inserted = insert(lockedMode.outputKey(), lockedBatchSize);
        if (inserted < lockedBatchSize) {
            inventory.insertItem(TeslaCoilInventory.SLOT_DUST, extractedDust, false);
            return false;
        }

        return true;
    }

    private boolean commitExtremeHighVoltage() {
        long requiredHighVoltage = getRequiredHighVoltageForBatch(lockedMode, lockedBatchSize);
        long extracted = extract(LightningKey.HIGH_VOLTAGE, requiredHighVoltage);
        if (extracted < requiredHighVoltage) {
            if (extracted > 0L) {
                insert(LightningKey.HIGH_VOLTAGE, extracted);
            }
            return false;
        }

        long inserted = insert(lockedMode.outputKey(), lockedBatchSize);
        if (inserted < lockedBatchSize) {
            insert(LightningKey.HIGH_VOLTAGE, extracted);
            return false;
        }

        return true;
    }

    private long simulateInsert(LightningKey key, long amount) {
        if (amount <= 0L) {
            return 0L;
        }
        var grid = getMainNode().getGrid();
        if (grid == null) {
            return 0L;
        }
        return grid.getStorageService().getInventory()
                .insert(key, amount, Actionable.SIMULATE, IActionSource.ofMachine(this));
    }

    private long insert(LightningKey key, long amount) {
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

    private long simulateExtract(LightningKey key, long amount) {
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

    private long extract(LightningKey key, long amount) {
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

    private void onInventoryChanged() {
        saveChanges();
        markForClientUpdate();
        logic.onStateChanged();
    }

    private void onEnergyChanged() {
        saveChanges();
        markForClientUpdate();
        logic.onStateChanged();
    }

    private long getBatchSizeForMode(TeslaCoilMode mode) {
        if (mode == TeslaCoilMode.HIGH_VOLTAGE) {
            int dustPerOperation = mode.requiredDust();
            if (dustPerOperation <= 0) {
                return 0L;
            }

            long craftable = inventory.getStackInSlot(TeslaCoilInventory.SLOT_DUST).getCount() / dustPerOperation;
            if (craftable <= 0L) {
                return 0L;
            }

            return Math.min(craftable, simulateInsert(mode.outputKey(), craftable));
        }

        return hasLocalPrerequisites(mode, 1L) && canCommitAgainstNetwork(mode, 1L) ? 1L : 0L;
    }

    private long getTotalEnergyFor(TeslaCoilMode mode, long batchSize) {
        if (mode == null || batchSize <= 0L) {
            return 0L;
        }
        return Math.multiplyExact(mode.totalEnergy(), batchSize);
    }

    private long getLockedTotalEnergy() {
        return getTotalEnergyFor(lockedMode, lockedBatchSize);
    }

    private int getRequiredDustForBatch(TeslaCoilMode mode, long batchSize) {
        return Math.toIntExact(Math.multiplyExact(mode.requiredDust(), batchSize));
    }

    private long getRequiredHighVoltageForBatch(TeslaCoilMode mode, long batchSize) {
        return Math.multiplyExact(mode.requiredHighVoltage(), batchSize);
    }
}
