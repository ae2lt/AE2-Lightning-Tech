package com.moakiee.ae2lt.blockentity;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.security.IActionHost;
import appeng.api.orientation.BlockOrientation;
import appeng.blockentity.grid.AENetworkBlockEntity;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocator;

import com.moakiee.ae2lt.grid.FrequencyBindingHelper;
import com.moakiee.ae2lt.grid.FrequencyBindingHost;
import com.moakiee.ae2lt.grid.OverloadedGridNodeOwner;
import com.moakiee.ae2lt.item.WeatherCondensateItem;
import com.moakiee.ae2lt.logic.WeatherControlHelper;
import com.moakiee.ae2lt.machine.atmosphericionizer.AtmosphericIonizerInventory;
import com.moakiee.ae2lt.machine.atmosphericionizer.AtmosphericIonizerLogic;
import com.moakiee.ae2lt.machine.atmosphericionizer.AtmosphericIonizerStatus;
import com.moakiee.ae2lt.machine.common.InsertOnlyAutomationInventory;
import com.moakiee.ae2lt.menu.AtmosphericIonizerMenu;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.block.AtmosphericIonizerBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.IItemHandlerModifiable;

public class AtmosphericIonizerBlockEntity extends AENetworkBlockEntity
        implements IActionHost, FrequencyBindingHost, OverloadedGridNodeOwner {
    private static final Logger LOG = LogUtils.getLogger();
    public static final int PROCESS_TICKS = 100;
    private static final double POWER_EPSILON = 0.01D;
    /** 雷暴凝核成功后,在 BE 正上方第 N 格召雷,使其落在研究反应场里。 */
    private static final int RITUAL_STRIKE_HEIGHT_OFFSET = 2;

    private static final String TAG_INVENTORY = "Inventory";
    private static final String TAG_CONSUMED_ENERGY = "ConsumedEnergy";
    private static final String TAG_PROCESSING_TICKS = "ProcessingTicks";
    private static final String TAG_LOCKED_TYPE = "LockedType";

    private final AtmosphericIonizerInventory inventory = new AtmosphericIonizerInventory(this::onInventoryChanged);
    private final IItemHandlerModifiable automationInventory = new InsertOnlyAutomationInventory(inventory);
    private final AtmosphericIonizerLogic logic;
    private final FrequencyBindingHelper frequencyBinding = new FrequencyBindingHelper(this);

    private WeatherCondensateItem.Type lockedType;
    private long consumedEnergy;
    private int processingTicksSpent;
    private boolean working;

    public AtmosphericIonizerBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.ATMOSPHERIC_IONIZER.get(), pos, blockState);
        this.logic = new AtmosphericIonizerLogic(this);
        getMainNode()
                .setIdlePowerUsage(0)
                .addService(appeng.api.networking.ticking.IGridTickable.class, logic);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AtmosphericIonizerBlockEntity be) {
        if (!level.isClientSide()) {
            be.frequencyBinding.serverTick();
        }
    }

    @Override
    public FrequencyBindingHelper getFrequencyBinding() {
        return frequencyBinding;
    }

    @Override
    public AENetworkBlockEntity getFrequencyBindingBlockEntity() {
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

    public AtmosphericIonizerInventory getInventory() {
        return inventory;
    }

    public IItemHandlerModifiable getAutomationInventory() {
        return automationInventory;
    }

    public WeatherCondensateItem.Type getSelectedType() {
        return lockedType != null ? lockedType : WeatherCondensateItem.getType(getInstalledCondensate());
    }

    public boolean hasLockedType() {
        return lockedType != null;
    }

    public ItemStack getInstalledCondensate() {
        return inventory.getStackInSlot(AtmosphericIonizerInventory.SLOT_CONDENSATE);
    }

    public boolean hasLocalStartPrerequisites() {
        return getSelectedType() != null;
    }

    public boolean hasLockedCondensateInput() {
        return lockedType != null && lockedType == WeatherCondensateItem.getType(getInstalledCondensate());
    }

    public boolean canOperateInCurrentDimension() {
        return level instanceof ServerLevel serverLevel && WeatherControlHelper.supportsWeather(serverLevel);
    }

    public boolean isSelectedWeatherAlreadyActive() {
        WeatherCondensateItem.Type selectedType = getSelectedType();
        return selectedType != null && isWeatherAlreadyActive(selectedType);
    }

    public boolean isLockedWeatherAlreadyActive() {
        return lockedType != null && isWeatherAlreadyActive(lockedType);
    }

    public boolean hasEnoughEnergyForSelectedStart() {
        return canExtractAEPower(requiredEnergyForTick(getSelectedType(), 0, 0L));
    }

    public long getConsumedEnergy() {
        return consumedEnergy;
    }

    public long getTotalEnergy() {
        WeatherCondensateItem.Type type = getSelectedType();
        return type == null ? 0L : type.totalEnergy();
    }

    public long getRequiredEnergyForNextTick() {
        return requiredEnergyForTick(lockedType, processingTicksSpent, consumedEnergy);
    }

    public boolean canExtractAEPower(long amount) {
        return extractAEPower(amount, Actionable.SIMULATE);
    }

    public boolean tryExtractAEPower(long amount) {
        return extractAEPower(amount, Actionable.MODULATE);
    }

    public boolean isReadyToCommit() {
        return lockedType != null
                && processingTicksSpent >= PROCESS_TICKS
                && consumedEnergy >= lockedType.totalEnergy();
    }

    public boolean lockSelectedCondensate() {
        WeatherCondensateItem.Type selectedType = WeatherCondensateItem.getType(getInstalledCondensate());
        if (selectedType == null) {
            return false;
        }

        lockedType = selectedType;
        saveChanges();
        markForUpdate();
        logic.onStateChanged();
        return true;
    }

    public void advanceProgress(long amount) {
        if (lockedType == null || amount <= 0L) {
            return;
        }

        consumedEnergy = Math.min(lockedType.totalEnergy(), consumedEnergy + amount);
        processingTicksSpent = Math.min(PROCESS_TICKS, processingTicksSpent + 1);
        saveChanges();
        markForUpdate();
    }

    public AtmosphericIonizerStatus getStatus() {
        WeatherCondensateItem.Type selectedType = getSelectedType();
        if (selectedType == null) {
            return AtmosphericIonizerStatus.IDLE;
        }

        if (!canOperateInCurrentDimension()) {
            return AtmosphericIonizerStatus.INVALID_DIMENSION;
        }

        if ((lockedType != null && isLockedWeatherAlreadyActive())
                || (lockedType == null && isSelectedWeatherAlreadyActive())) {
            return AtmosphericIonizerStatus.TARGET_ALREADY_ACTIVE;
        }

        if (lockedType == null) {
            return hasEnoughEnergyForSelectedStart()
                    ? AtmosphericIonizerStatus.READY
                    : AtmosphericIonizerStatus.WAITING_AE;
        }

        if (!hasLockedCondensateInput()) {
            return AtmosphericIonizerStatus.WAITING_INPUT;
        }

        if (isReadyToCommit()) {
            return AtmosphericIonizerStatus.READY;
        }

        return canExtractAEPower(getRequiredEnergyForNextTick())
                ? AtmosphericIonizerStatus.CHARGING
                : AtmosphericIonizerStatus.WAITING_AE;
    }

    public boolean commitLockedCondensate() {
        if (!(level instanceof ServerLevel serverLevel)
                || lockedType == null
                || !hasLockedCondensateInput()
                || !isInstalledInWorld()) {
            return false;
        }
        if (!WeatherControlHelper.supportsWeather(serverLevel) || lockedType.isActive(serverLevel)) {
            return false;
        }

        ItemStack extracted = inventory.extractItem(AtmosphericIonizerInventory.SLOT_CONDENSATE, 1, false);
        if (extracted.isEmpty()) {
            return false;
        }

        WeatherCondensateItem.Type committedType = lockedType;
        if (!committedType.apply(serverLevel, serverLevel.random)) {
            LOG.debug("[ae2lt/ionizer] commit aborted: apply() failed for type={} at {}", committedType, worldPosition);
            inventory.insertItem(AtmosphericIonizerInventory.SLOT_CONDENSATE, extracted, false);
            return false;
        }
        LOG.debug("[ae2lt/ionizer] commit OK: type={} at {}", committedType, worldPosition);

        if (committedType == WeatherCondensateItem.Type.THUNDERSTORM) {
            summonRitualLightning(serverLevel);
        }

        lockedType = null;
        consumedEnergy = 0L;
        processingTicksSpent = 0;
        setWorking(false);
        saveChanges();
        markForUpdate();
        logic.onStateChanged();
        return true;
    }

    public void openMenu(Player player, MenuLocator locator) {
        MenuOpener.open(AtmosphericIonizerMenu.TYPE, player, locator);
    }

    public boolean isWorking() {
        return working;
    }

    public boolean isInstalledInWorld() {
        if (level == null || isRemoved()) {
            return false;
        }

        BlockState state = level.getBlockState(worldPosition);
        return state.is(ModBlocks.ATMOSPHERIC_IONIZER.get())
                && state.hasProperty(AtmosphericIonizerBlock.HALF)
                && state.getValue(AtmosphericIonizerBlock.HALF) == DoubleBlockHalf.LOWER
                && level.getBlockEntity(worldPosition) == this;
    }

    public void setWorking(boolean working) {
        boolean changed = this.working != working;
        this.working = working;
        if (level != null) {
            BlockState state = getBlockState();
            if (state.hasProperty(AtmosphericIonizerBlock.WORKING)
                    && state.getValue(AtmosphericIonizerBlock.WORKING) != working) {
                level.setBlock(worldPosition, state.setValue(AtmosphericIonizerBlock.WORKING, working), Block.UPDATE_ALL);
            } else if (changed) {
                markForUpdate();
            }
        }
    }

    public void cancelProcessingForRemoval() {
        lockedType = null;
        consumedEnergy = 0L;
        processingTicksSpent = 0;
        working = false;
    }

    @Override
    public void onReady() {
        super.onReady();
        frequencyBinding.onReady();
        setWorking(lockedType != null);
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

    /**
     * 雷暴凝核成功的一瞬间,立即在 BE 正上方召唤一束原版闪电。
     * 研究仪式是否成立,交由 {@code LightningItemTransformationHandler}
     * + {@code ResearchRitualService} 按反应场内的物品判定,这里只负责"触发点"。
     */
    private void summonRitualLightning(ServerLevel serverLevel) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(serverLevel);
        if (bolt == null) {
            LOG.warn("[ae2lt/ionizer] summonRitualLightning: EntityType.LIGHTNING_BOLT.create returned null at {}",
                    worldPosition);
            return;
        }
        BlockPos strikePos = worldPosition.above(RITUAL_STRIKE_HEIGHT_OFFSET);
        bolt.moveTo(Vec3.atBottomCenterOf(strikePos));
        bolt.setVisualOnly(false);
        serverLevel.addFreshEntity(bolt);
        LOG.info("[ae2lt/ionizer] thunderstorm nucleation -> spawn lightning at {} (ionizer={})", strikePos,
                worldPosition);
    }

    @Override
    public void saveAdditional(CompoundTag data) {
        super.saveAdditional(data);
        inventory.saveToTag(data, TAG_INVENTORY);
        data.putLong(TAG_CONSUMED_ENERGY, consumedEnergy);
        data.putInt(TAG_PROCESSING_TICKS, processingTicksSpent);
        if (lockedType != null) {
            data.putString(TAG_LOCKED_TYPE, lockedType.getSerializedName());
        } else {
            data.remove(TAG_LOCKED_TYPE);
        }
        frequencyBinding.save(data);
    }

    @Override
    public void loadTag(CompoundTag data) {
        super.loadTag(data);
        inventory.loadFromTag(data, TAG_INVENTORY);
        lockedType = data.contains(TAG_LOCKED_TYPE)
                ? WeatherCondensateItem.Type.fromName(data.getString(TAG_LOCKED_TYPE))
                : null;
        consumedEnergy = Math.max(0L, data.getLong(TAG_CONSUMED_ENERGY));
        processingTicksSpent = Math.max(0, data.getInt(TAG_PROCESSING_TICKS));
        frequencyBinding.load(data);

        if (lockedType == null) {
            consumedEnergy = 0L;
            processingTicksSpent = 0;
        } else {
            consumedEnergy = Math.min(consumedEnergy, lockedType.totalEnergy());
            processingTicksSpent = Math.min(processingTicksSpent, PROCESS_TICKS);
        }
        working = lockedType != null;
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        ItemStack condensate = getInstalledCondensate();
        if (!condensate.isEmpty()) {
            drops.add(condensate.copy());
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        inventory.clear();
    }

    @Override
    public void exportSettings(appeng.util.SettingsFrom mode,
                               net.minecraft.nbt.CompoundTag output,
                               @org.jetbrains.annotations.Nullable Player player) {
        super.exportSettings(mode, output, player);
        FrequencyBindingHelper.exportMemorySettings(mode, output, getFrequencyId());
    }

    @Override
    public void importSettings(appeng.util.SettingsFrom mode,
                               net.minecraft.nbt.CompoundTag input,
                               @org.jetbrains.annotations.Nullable Player player) {
        super.importSettings(mode, input, player);
        FrequencyBindingHelper.importMemorySettings(mode, input, this::setFrequency);
    }

    @Override
    protected net.minecraft.world.item.Item getItemFromBlockEntity() {
        return ModBlocks.ATMOSPHERIC_IONIZER.get().asItem();
    }

    @Override
    public IGridNode getActionableNode() {
        return getMainNode().getNode();
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.of(Direction.DOWN);
    }

    private boolean isWeatherAlreadyActive(WeatherCondensateItem.Type type) {
        return level instanceof ServerLevel serverLevel && type.isActive(serverLevel);
    }

    private long requiredEnergyForTick(WeatherCondensateItem.Type type, int ticksSpent, long consumed) {
        if (type == null || ticksSpent >= PROCESS_TICKS) {
            return 0L;
        }

        long remainingEnergy = Math.max(0L, type.totalEnergy() - consumed);
        int remainingTicks = Math.max(1, PROCESS_TICKS - ticksSpent);
        return (remainingEnergy + remainingTicks - 1L) / remainingTicks;
    }

    private void onInventoryChanged() {
        saveChanges();
        markForUpdate();
        logic.onStateChanged();
    }

    private boolean extractAEPower(long amount, Actionable mode) {
        if (amount <= 0L) {
            return true;
        }

        var grid = getMainNode().getGrid();
        if (grid == null) {
            return false;
        }

        double extracted = grid.getEnergyService().extractAEPower(amount, mode, PowerMultiplier.CONFIG);
        return extracted >= amount - POWER_EPSILON;
    }
}
