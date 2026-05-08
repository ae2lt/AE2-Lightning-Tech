package com.moakiee.ae2lt.blockentity;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;

import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.orientation.BlockOrientation;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;

import com.moakiee.ae2lt.block.LightningCollectorBlock;
import com.moakiee.ae2lt.api.event.LightningCollectedEvent;
import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.grid.FrequencyBindingHelper;
import com.moakiee.ae2lt.grid.FrequencyBindingHost;
import com.moakiee.ae2lt.item.ElectroChimeCrystalItem;
import com.moakiee.ae2lt.machine.common.InsertOnlyAutomationInventory;
import com.moakiee.ae2lt.machine.lightningcollector.LightningCollectorInventory;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.menu.LightningCollectorMenu;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

public class LightningCollectorBlockEntity extends AENetworkedBlockEntity implements IActionHost, FrequencyBindingHost {
    private static final Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static final String TAG_INVENTORY = "Inventory";
    private static final String TAG_COOLDOWN = "CooldownTicks";
    private static final String TAG_WORKING_TICKS = "WorkingTicks";
    private static final int WORKING_DURATION_TICKS = 20;
    private static final long NATURAL_CULTIVATION_DEBOUNCE_TICKS = 20L;
    private static boolean warnedInvalidHighVoltageBaseRange;
    private static boolean warnedInvalidExtremeVoltageBaseRange;

    private final LightningCollectorInventory inventory = new LightningCollectorInventory(this::onInventoryChanged);
    private final IItemHandlerModifiable automationInventory = new InsertOnlyAutomationInventory(inventory);
    private final FrequencyBindingHelper frequencyBinding = new FrequencyBindingHelper(this);

    private int cooldownTicks;
    private int workingTicks;
    private long lastCaptureGameTime = Long.MIN_VALUE;
    private long lastNaturalCultivationGameTime = Long.MIN_VALUE;

    public LightningCollectorBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.LIGHTNING_COLLECTOR.get(), pos, blockState);
        getMainNode().setIdlePowerUsage(0.0D);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LightningCollectorBlockEntity blockEntity) {
        if (!(level instanceof ServerLevel)) {
            return;
        }

        blockEntity.frequencyBinding.serverTick();

        if (blockEntity.cooldownTicks > 0) {
            blockEntity.cooldownTicks--;
            if (blockEntity.cooldownTicks == 0) {
                blockEntity.saveChanges();
                blockEntity.markForClientUpdate();
            }
        }

        if (blockEntity.workingTicks > 0) {
            blockEntity.workingTicks--;
            if (blockEntity.workingTicks == 0) {
                blockEntity.updateWorkingBlockState(false);
                blockEntity.saveChanges();
            }
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

    public IItemHandlerModifiable getAutomationInventory() {
        return automationInventory;
    }

    public LightningCollectorInventory getInventory() {
        return inventory;
    }

    public int getCooldownTicks() {
        return cooldownTicks;
    }

    public ItemStack getInstalledCrystal() {
        return inventory.getStackInSlot(LightningCollectorInventory.SLOT_CRYSTAL);
    }

    public int getCatalysisValue() {
        ItemStack crystal = getInstalledCrystal();
        return crystal.isEmpty() ? 0 : ElectroChimeCrystalItem.getCatalysisValue(crystal);
    }

    public OutputPreview getPreview(LightningKey.Tier tier) {
        ItemStack crystal = getInstalledCrystal();
        boolean extremeHighVoltage = tier == LightningKey.Tier.EXTREME_HIGH_VOLTAGE;
        if (crystal.isEmpty()) {
            int baseMin = extremeHighVoltage
                    ? AE2LTCommonConfig.lightningCollectorEhvBaseMin()
                    : AE2LTCommonConfig.lightningCollectorHvBaseMin();
            int baseMax = extremeHighVoltage
                    ? AE2LTCommonConfig.lightningCollectorEhvBaseMax()
                    : AE2LTCommonConfig.lightningCollectorHvBaseMax();
            if (baseMin > baseMax) {
                warnInvalidBaseRange(extremeHighVoltage, baseMin, baseMax);
            }
            return new OutputPreview(
                    Math.min(baseMin, baseMax),
                    Math.max(baseMin, baseMax));
        }

        if (crystal.is(ModItems.PERFECT_ELECTRO_CHIME_CRYSTAL.get())) {
            int output = extremeHighVoltage
                    ? AE2LTCommonConfig.lightningCollectorPerfectEhvOutput()
                    : AE2LTCommonConfig.lightningCollectorPerfectHvOutput();
            return new OutputPreview(output, output);
        }

        double progress = ElectroChimeCrystalItem.getCatalysisPercent(crystal);
        double center = Mth.lerp(
                progress,
                extremeHighVoltage
                        ? AE2LTCommonConfig.lightningCollectorEhvCrystalStart()
                        : AE2LTCommonConfig.lightningCollectorHvCrystalStart(),
                extremeHighVoltage
                        ? AE2LTCommonConfig.lightningCollectorEhvCrystalEnd()
                        : AE2LTCommonConfig.lightningCollectorHvCrystalEnd());
        int spread = Math.max(1, Mth.floor(center * AE2LTCommonConfig.lightningCollectorSpreadRatio()));
        int min = Math.max(1, Mth.floor(center) - spread);
        int max = Math.max(min, Mth.ceil(center) + spread);
        return new OutputPreview(min, max);
    }

    public boolean canCaptureLightning() {
        return cooldownTicks <= 0 && !hasCapturedThisTick();
    }

    public boolean captureLightning(boolean naturalWeatherLightning) {
        if (!(level instanceof ServerLevel serverLevel) || cooldownTicks > 0 || hasCapturedThisTick(serverLevel)) {
            return false;
        }

        var grid = getMainNode().getGrid();
        if (grid == null) {
            return false;
        }

        LightningKey.Tier tier = naturalWeatherLightning
                ? LightningKey.Tier.EXTREME_HIGH_VOLTAGE
                : LightningKey.Tier.HIGH_VOLTAGE;
        OutputPreview preview = getPreview(tier);
        int rolledOutput = preview.roll(serverLevel.getRandom());
        if (rolledOutput <= 0) {
            return false;
        }

        // Public API hook: addons may cancel the capture entirely or rewrite the
        // amount before it reaches the grid. See com.moakiee.ae2lt.api.event.
        LightningCollectedEvent collectedEvent = new LightningCollectedEvent(
                serverLevel,
                worldPosition,
                LightningKey.toApiTier(tier),
                rolledOutput,
                naturalWeatherLightning);
        NeoForge.EVENT_BUS.post(collectedEvent);
        if (collectedEvent.isCanceled()) {
            return false;
        }

        long amountToInsert = collectedEvent.getAmount();
        long inserted = amountToInsert > 0
                ? grid.getStorageService().getInventory().insert(
                        LightningKey.of(tier),
                        amountToInsert,
                        Actionable.MODULATE,
                        IActionSource.ofMachine(this))
                : 0L;

        boolean captured = inserted > 0;
        if (!captured) {
            return false;
        }

        if (tier == LightningKey.Tier.EXTREME_HIGH_VOLTAGE && canCultivateFromNaturalStrike(serverLevel)) {
            if (cultivateCrystal(serverLevel.getRandom())) {
                lastNaturalCultivationGameTime = serverLevel.getGameTime();
            }
        }

        this.lastCaptureGameTime = serverLevel.getGameTime();
        this.cooldownTicks = AE2LTCommonConfig.lightningCollectorCooldownTicks();
        this.workingTicks = WORKING_DURATION_TICKS;
        updateWorkingBlockState(true);
        saveChanges();
        markForClientUpdate();
        return true;
    }

    public void openMenu(Player player, MenuHostLocator locator) {
        MenuOpener.open(LightningCollectorMenu.TYPE, player, locator);
    }

    @Override
    public void saveAdditional(ValueOutput data) {
        super.saveAdditional(data);
        inventory.saveToTag(data, TAG_INVENTORY);
        data.putInt(TAG_COOLDOWN, cooldownTicks);
        data.putInt(TAG_WORKING_TICKS, workingTicks);
        frequencyBinding.save(data);
    }

    @Override
    public void loadTag(ValueInput data) {
        super.loadTag(data);
        inventory.loadFromTag(data, TAG_INVENTORY);
        cooldownTicks = Math.max(0, data.getIntOr(TAG_COOLDOWN, 0));
        workingTicks = Math.max(0, data.getIntOr(TAG_WORKING_TICKS, 0));
        frequencyBinding.load(data);
    }

    @Override
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(data, getInstalledCrystal());
        data.writeVarInt(cooldownTicks);
    }

    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        ItemStack oldCrystal = getInstalledCrystal();
        ItemStack newCrystal = ItemStack.OPTIONAL_STREAM_CODEC.decode(data);
        if (!ItemStack.matches(oldCrystal, newCrystal)) {
            inventory.setClientRenderStack(newCrystal);
            changed = true;
        }

        int newCooldown = data.readVarInt();
        if (newCooldown != cooldownTicks) {
            cooldownTicks = newCooldown;
            changed = true;
        }
        return changed;
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        ItemStack crystal = getInstalledCrystal();
        if (!crystal.isEmpty()) {
            drops.add(crystal.copy());
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
        FrequencyBindingHelper.exportMemorySettings(mode, builder, getFrequencyId());
    }

    @Override
    public void importSettings(appeng.util.SettingsFrom mode,
                               net.minecraft.core.component.DataComponentMap input,
                               @org.jetbrains.annotations.Nullable Player player) {
        super.importSettings(mode, input, player);
        FrequencyBindingHelper.importMemorySettings(mode, input, this::setFrequency);
    }

    @Override
    protected net.minecraft.world.item.Item getItemFromBlockEntity() {
        return ModBlocks.LIGHTNING_COLLECTOR.get().asItem();
    }

    @Override
    public IGridNode getActionableNode() {
        return getMainNode().getNode();
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.allOf(Direction.class);
    }

    private boolean canCultivateFromNaturalStrike(ServerLevel serverLevel) {
        long gameTime = serverLevel.getGameTime();
        return lastNaturalCultivationGameTime == Long.MIN_VALUE
                || gameTime - lastNaturalCultivationGameTime >= NATURAL_CULTIVATION_DEBOUNCE_TICKS;
    }

    private boolean hasCapturedThisTick() {
        return level instanceof ServerLevel serverLevel && hasCapturedThisTick(serverLevel);
    }

    private boolean hasCapturedThisTick(ServerLevel serverLevel) {
        return lastCaptureGameTime == serverLevel.getGameTime();
    }

    private boolean cultivateCrystal(RandomSource random) {
        ItemStack crystal = getInstalledCrystal();
        if (crystal.isEmpty() || crystal.is(ModItems.PERFECT_ELECTRO_CHIME_CRYSTAL.get())) {
            return false;
        }

        int feed = ElectroChimeCrystalItem.rollCatalysisFeed(random);
        int catalysis = ElectroChimeCrystalItem.addCatalysis(crystal, feed);
        if (catalysis >= ElectroChimeCrystalItem.getMaxCatalysis()) {
            inventory.setStackInSlot(LightningCollectorInventory.SLOT_CRYSTAL,
                    new ItemStack(ModItems.PERFECT_ELECTRO_CHIME_CRYSTAL.get()));
        }
        return true;
    }

    private static void warnInvalidBaseRange(boolean extremeHighVoltage, int min, int max) {
        if (extremeHighVoltage) {
            if (warnedInvalidExtremeVoltageBaseRange) {
                return;
            }
            warnedInvalidExtremeVoltageBaseRange = true;
        } else {
            if (warnedInvalidHighVoltageBaseRange) {
                return;
            }
            warnedInvalidHighVoltageBaseRange = true;
        }

        LOG.warn(
                "Invalid lightning collector {} base output range: min={} max={}. Swapping values as a fallback.",
                extremeHighVoltage ? "extremeHighVoltage" : "highVoltage",
                min,
                max);
    }

    private void onInventoryChanged() {
        saveChanges();
        markForClientUpdate();
    }

    private void updateWorkingBlockState(boolean working) {
        if (level == null) {
            return;
        }
        BlockState state = getBlockState();
        if (state.hasProperty(LightningCollectorBlock.WORKING)
                && state.getValue(LightningCollectorBlock.WORKING) != working) {
            level.setBlock(worldPosition, state.setValue(LightningCollectorBlock.WORKING, working), Block.UPDATE_ALL);
        }
    }

    @Override
    public void onReady() {
        super.onReady();
        frequencyBinding.onReady();
        updateWorkingBlockState(workingTicks > 0);
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

    public record OutputPreview(int min, int max) {
        public int roll(RandomSource random) {
            if (max <= min) {
                return min;
            }
            return random.nextInt(max - min + 1) + min;
        }
    }
}
