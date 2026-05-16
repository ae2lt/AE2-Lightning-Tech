package com.moakiee.ae2lt.blockentity;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;

import appeng.api.implementations.blockentities.IWirelessAccessPoint;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.util.DimensionalBlockPos;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;

import com.moakiee.ae2lt.blockentity.workbench.DeviceWorkbenchAdapter;
import com.moakiee.ae2lt.blockentity.workbench.DeviceWorkbenchAdapters;
import com.moakiee.ae2lt.blockentity.workbench.StructuralSlotSpec;
import com.moakiee.ae2lt.device.DeviceItem;
import com.moakiee.ae2lt.menu.OverloadDeviceWorkbenchMenu;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModBlocks;

public class OverloadDeviceWorkbenchBlockEntity extends AENetworkedBlockEntity
        implements InternalInventoryHost, IWirelessAccessPoint {
    private static final String TAG_DEVICE_INV = "DeviceInv";

    private final AppEngInternalInventory deviceInventory = new AppEngInternalInventory(this, 1, 1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.getItem() instanceof DeviceItem;
        }
    };

    public OverloadDeviceWorkbenchBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.OVERLOAD_DEVICE_WORKBENCH.get(), pos, blockState);
        getMainNode().setIdlePowerUsage(0.0D);
    }

    public AppEngInternalInventory getDeviceInventory() {
        return deviceInventory;
    }

    public ItemStack getInstalledDevice() {
        return deviceInventory.getStackInSlot(0);
    }

    public boolean hasInstalledDevice() {
        return !getInstalledDevice().isEmpty() && currentAdapter() != null;
    }

    public @Nullable DeviceWorkbenchAdapter currentAdapter() {
        return DeviceWorkbenchAdapters.get(getInstalledDevice()).orElse(null);
    }

    public List<StructuralSlotSpec> getStructuralSlots() {
        var adapter = currentAdapter();
        return adapter == null ? List.of() : adapter.structuralSlots();
    }

    public ItemStack getStructuralSlot(HolderLookup.Provider registries, StructuralSlotSpec spec) {
        var adapter = currentAdapter();
        return adapter == null
                ? ItemStack.EMPTY
                : adapter.getStructuralSlot(getInstalledDevice(), registries, spec);
    }

    public boolean canPlaceStructural(
            HolderLookup.Provider registries,
            StructuralSlotSpec spec,
            ItemStack stack) {
        var adapter = currentAdapter();
        return adapter != null
                && adapter.canPlaceStructural(getInstalledDevice(), registries, spec, stack);
    }

    public void setStructuralSlot(
            HolderLookup.Provider registries,
            StructuralSlotSpec spec,
            ItemStack stack) {
        var adapter = currentAdapter();
        if (adapter == null) {
            return;
        }
        adapter.setStructuralSlot(getInstalledDevice(), registries, spec, stack);
        adapter.onModulesChanged(getInstalledDevice(), registries, dist());
        saveChanges();
    }

    public ItemStack removeStructuralSlot(
            HolderLookup.Provider registries,
            StructuralSlotSpec spec,
            int amount) {
        var adapter = currentAdapter();
        if (adapter == null || amount <= 0) {
            return ItemStack.EMPTY;
        }
        var removed = adapter.removeStructuralSlot(getInstalledDevice(), registries, spec, amount);
        if (!removed.isEmpty()) {
            adapter.onModulesChanged(getInstalledDevice(), registries, dist());
            saveChanges();
        }
        return removed;
    }

    public boolean mayPickupStructural(
            HolderLookup.Provider registries,
            StructuralSlotSpec spec,
            Player player,
            ItemStack carried) {
        var adapter = currentAdapter();
        return adapter != null
                && adapter.mayPickupStructural(getInstalledDevice(), registries, spec, player, carried);
    }

    public List<ItemStack> getModuleList(HolderLookup.Provider registries) {
        var adapter = currentAdapter();
        return adapter == null
                ? List.of()
                : adapter.listModuleEntries(getInstalledDevice(), registries);
    }

    public boolean canInstallOneModule(HolderLookup.Provider registries, ItemStack candidate) {
        var adapter = currentAdapter();
        return adapter != null
                && adapter.canInstallOne(getInstalledDevice(), registries, candidate);
    }

    public boolean installOneModule(HolderLookup.Provider registries, ItemStack candidate) {
        var adapter = currentAdapter();
        if (adapter == null || candidate == null || candidate.isEmpty()) {
            return false;
        }
        var device = getInstalledDevice();
        if (!adapter.installOne(device, registries, candidate)) {
            return false;
        }
        adapter.onModulesChanged(device, registries, dist());
        saveChanges();
        return true;
    }

    public ItemStack uninstallOneModule(HolderLookup.Provider registries, String typeId) {
        var adapter = currentAdapter();
        if (adapter == null) {
            return ItemStack.EMPTY;
        }
        var device = getInstalledDevice();
        var detached = adapter.uninstallOne(device, registries, typeId);
        if (detached.isEmpty()) {
            return ItemStack.EMPTY;
        }
        adapter.onModulesChanged(device, registries, dist());
        saveChanges();
        return detached;
    }

    public ItemStack uninstallAllOfModule(HolderLookup.Provider registries, String typeId) {
        var adapter = currentAdapter();
        if (adapter == null) {
            return ItemStack.EMPTY;
        }
        var device = getInstalledDevice();
        var detached = adapter.uninstallAll(device, registries, typeId);
        if (detached.isEmpty()) {
            return ItemStack.EMPTY;
        }
        adapter.onModulesChanged(device, registries, dist());
        saveChanges();
        return detached;
    }

    public String moduleTypeId(ItemStack stack) {
        var adapter = currentAdapter();
        return adapter == null ? "" : adapter.moduleTypeId(stack);
    }

    public int moduleMaxInstallAmount(ItemStack stack) {
        var adapter = currentAdapter();
        return adapter == null ? 0 : adapter.maxInstallAmount(stack);
    }

    public int baseOverloadBudget(HolderLookup.Provider registries) {
        var adapter = currentAdapter();
        return adapter == null ? 0 : adapter.baseOverloadBudget(getInstalledDevice(), registries);
    }

    public int currentIdleOverload(HolderLookup.Provider registries) {
        var adapter = currentAdapter();
        return adapter == null ? 0 : adapter.currentIdleOverload(getInstalledDevice(), registries);
    }

    public void openMenu(Player player, MenuHostLocator locator) {
        MenuOpener.open(OverloadDeviceWorkbenchMenu.TYPE, player, locator);
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {
        saveChanges();
        markForClientUpdate();
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        if (inv == deviceInventory) {
            bindInsertedDevice();
            saveChanges();
        }
    }

    @Override
    public boolean isClientSide() {
        return level != null && level.isClientSide();
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        deviceInventory.writeToNBT(data, TAG_DEVICE_INV, registries);
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        deviceInventory.readFromNBT(data, TAG_DEVICE_INV, registries);
        bindInsertedDevice();
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        var device = getInstalledDevice();
        if (!device.isEmpty()) {
            drops.add(device.copy());
        }
    }

    @Override
    protected net.minecraft.world.item.Item getItemFromBlockEntity() {
        return ModBlocks.OVERLOAD_DEVICE_WORKBENCH.get().asItem();
    }

    @Override
    public DimensionalBlockPos getLocation() {
        return new DimensionalBlockPos(level, worldPosition);
    }

    @Override
    public double getRange() {
        return Double.MAX_VALUE;
    }

    @Override
    public boolean isActive() {
        return getMainNode().isActive() && getMainNode().getGrid() != null;
    }

    @Override
    public IGrid getGrid() {
        return getMainNode().getGrid();
    }

    @Override
    public IGridNode getActionableNode() {
        return getMainNode().getNode();
    }

    private void bindInsertedDevice() {
        if (level == null || level.isClientSide()) {
            return;
        }
        var device = getInstalledDevice();
        var adapter = currentAdapter();
        if (device.isEmpty() || adapter == null) {
            return;
        }
        adapter.onDeviceInserted(device);
        adapter.networkBinding().bind(device, GlobalPos.of(level.dimension(), worldPosition));
    }

    private Dist dist() {
        return isClientSide() ? Dist.CLIENT : Dist.DEDICATED_SERVER;
    }
}
