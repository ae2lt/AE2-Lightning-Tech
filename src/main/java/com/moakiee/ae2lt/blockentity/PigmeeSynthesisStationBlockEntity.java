package com.moakiee.ae2lt.blockentity;

import appeng.api.AECapabilities;
import appeng.api.inventories.InternalInventory;
import appeng.api.storage.ILinkStatus;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.MEStorage;
import appeng.api.util.IConfigManager;
import appeng.api.config.Settings;
import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.config.ViewItems;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.parts.reporting.CraftingTerminalPart;
import appeng.me.storage.CompositeStorage;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.parts.automation.StackWorldBehaviors;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import com.moakiee.ae2lt.block.PigmeeSynthesisStationBlock;
import com.moakiee.ae2lt.menu.PigmeeSynthesisStationMenu;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModBlocks;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import appeng.api.behaviors.ExternalStorageStrategy;
import appeng.api.stacks.AEKeyType;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Terminal host for {@link PigmeeSynthesisStationBlock}.
 *
 * <p>Only external-storage strategies are accepted from neighbours. In
 * particular, a neighbour exposing {@link AECapabilities#ME_STORAGE} is
 * skipped entirely; this keeps the station from becoming a disguised terminal
 * merely by placing it next to an interface.</p>
 */
public final class PigmeeSynthesisStationBlockEntity extends AEBaseBlockEntity
        implements ITerminalHost, InternalInventoryHost, IEnergySource {
    public static final ResourceLocation INV_CRAFTING =
            CraftingTerminalPart.INV_CRAFTING;

    private static final String TAG_CRAFTING = "CraftingInventory";
    private static final MEStorage EMPTY_STORAGE = new CompositeStorage(Map.of());

    private final AppEngInternalInventory craftingInventory =
            new AppEngInternalInventory(this, 9);
    private final IConfigManager configManager = IConfigManager.builder(this::saveChanges)
            .registerSetting(Settings.SORT_BY, SortOrder.NAME)
            .registerSetting(Settings.SORT_DIRECTION, SortDir.ASCENDING)
            .registerSetting(Settings.VIEW_MODE, ViewItems.ALL)
            .build();

    private final Map<Direction, Map<AEKeyType, ExternalStorageStrategy>> strategiesBySide =
            new EnumMap<>(Direction.class);

    // AE2 captures this object when opening the menu. Resolve the live target on
    // every operation so removed/replaced containers cannot remain accessible.
    private final MEStorage terminalStorage = new MEStorage() {
        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            return findAdjacentStorage().insert(what, amount, mode, source);
        }

        @Override
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            return findAdjacentStorage().extract(what, amount, mode, source);
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            findAdjacentStorage().getAvailableStacks(out);
        }

        @Override
        public Component getDescription() {
            return Component.translatable("block.ae2lt.pigmee_synthesis_station");
        }
    };

    public PigmeeSynthesisStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PIGMEE_SYNTHESIS_STATION.get(), pos, state);
    }

    @Override
    public MEStorage getInventory() {
        return terminalStorage;
    }

    @Override
    public ILinkStatus getLinkStatus() {
        return findAdjacentStorage() != EMPTY_STORAGE
                ? ILinkStatus.ofConnected()
                : ILinkStatus.ofDisconnected(Component.translatable(
                        "ae2lt.pigmee_synthesis_station.no_capability"));
    }

    /** Manual crafting and adjacent inventory access do not require AE power. */
    @Override
    public double extractAEPower(double amount, Actionable mode, PowerMultiplier multiplier) {
        return amount;
    }

    @Override
    public IConfigManager getConfigManager() {
        return configManager;
    }

    @Override
    public InternalInventory getSubInventory(ResourceLocation id) {
        return INV_CRAFTING.equals(id)
                ? craftingInventory
                : InternalInventory.empty();
    }

    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        if (level != null && !level.isClientSide()) {
            MenuOpener.open(
                    PigmeeSynthesisStationMenu.TYPE,
                    player,
                    MenuLocators.forBlockEntity(this));
        }
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return ModBlocks.PIGMEE_SYNTHESIS_STATION.get().asItem().getDefaultInstance();
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inventory) {
        saveChanges();
    }

    @Override
    public boolean isClientSide() {
        return level == null || level.isClientSide();
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        for (ItemStack stack : craftingInventory) {
            if (!stack.isEmpty()) {
                drops.add(stack.copy());
            }
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        craftingInventory.clear();
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        craftingInventory.writeToNBT(tag, TAG_CRAFTING, registries);
        configManager.writeToNBT(tag, registries);
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        craftingInventory.readFromNBT(tag, TAG_CRAFTING, registries);
        configManager.readFromNBT(tag, registries);
    }

    @Override
    public void setRemoved() {
        strategiesBySide.clear();
        super.setRemoved();
    }

    @Override
    public void clearRemoved() {
        strategiesBySide.clear();
        super.clearRemoved();
    }

    private MEStorage findAdjacentStorage() {
        if (level == null || level.isClientSide() || isRemoved()) {
            return EMPTY_STORAGE;
        }

        var serverLevel = (net.minecraft.server.level.ServerLevel) level;
        for (Direction side : Direction.values()) {
            BlockPos targetPos = worldPosition.relative(side);
            if (!level.hasChunkAt(targetPos)) {
                continue;
            }

            BlockEntity target = level.getBlockEntity(targetPos);
            BlockState targetState = level.getBlockState(targetPos);

            // ME storage is intentionally not a valid station source. This is
            // the important distinction from simply mounting an interface.
            if (level.getCapability(
                    AECapabilities.ME_STORAGE,
                    targetPos,
                    targetState,
                    target,
                    side.getOpposite()) != null) {
                continue;
            }

            var strategies = strategiesBySide.computeIfAbsent(side, direction ->
                    StackWorldBehaviors.createExternalStorageStrategies(
                            serverLevel, targetPos, direction.getOpposite()));
            if (strategies.isEmpty()) {
                continue;
            }

            Map<AEKeyType, MEStorage> wrappers =
                    new IdentityHashMap<>(strategies.size());
            for (var entry : strategies.entrySet()) {
                MEStorage wrapper = entry.getValue().createWrapper(false, this::saveChanges);
                if (wrapper != null) {
                    wrappers.put(entry.getKey(), wrapper);
                }
            }
            if (!wrappers.isEmpty()) {
                return new CompositeStorage(wrappers);
            }
        }
        return EMPTY_STORAGE;
    }
}
