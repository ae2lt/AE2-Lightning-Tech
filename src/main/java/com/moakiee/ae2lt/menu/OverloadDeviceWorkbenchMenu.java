package com.moakiee.ae2lt.menu;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import appeng.api.inventories.InternalInventory;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.slot.AppEngSlot;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.blockentity.OverloadDeviceWorkbenchBlockEntity;
import com.moakiee.ae2lt.blockentity.workbench.StructuralSlotSpec;
import com.moakiee.ae2lt.device.DeviceItem;
import com.moakiee.ae2lt.device.DeviceKind;
import com.moakiee.ae2lt.device.DeviceSlotType;
import com.moakiee.ae2lt.menu.hub.DeviceHubDisplayRules;
import com.moakiee.ae2lt.celestweave.BaseCelestweaveArmorItem;

public class OverloadDeviceWorkbenchMenu extends AEBaseMenu {
    public static final MenuType<OverloadDeviceWorkbenchMenu> TYPE = MenuTypeBuilder
            .create(OverloadDeviceWorkbenchMenu::new, OverloadDeviceWorkbenchBlockEntity.class)
            .withMenuTitle(host -> Component.translatable("block.ae2lt.overload_device_workbench"))
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(
                    AE2LightningTech.MODID, "overload_device_workbench"));

    public static final int DEVICE_X = 13;
    public static final int DEVICE_Y = 20;
    public static final int STRUCTURAL_X = 13;
    public static final int STRUCTURAL_Y = 48;
    public static final int STRUCTURAL_SPACING = 20;
    public static final int MAX_STRUCTURAL_SLOTS = 1;

    public static final int INPUT_X = 13;
    public static final int INPUT_Y = 74;

    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 161;
    public static final int HOTBAR_Y = 219;

    @GuiSync(0)
    public int devicePresent;
    @GuiSync(1)
    public int moduleTypeCount;
    @GuiSync(2)
    public long energyCapacity;
    @GuiSync(3)
    public int coreInstalled;
    @GuiSync(4)
    public long energyStored;
    @GuiSync(5)
    public int installProgress;
    @GuiSync(6)
    public int gridConnected;
    @GuiSync(7)
    public int railgunDevice;
    @GuiSync(8)
    public int moduleUnitCount;
    @GuiSync(9)
    public int moduleSlotCount;

    public static final int INSTALL_TICKS = 20;

    private static final List<SlotSemantic> STRUCTURAL_SEMANTICS = List.of(
            Ae2ltSlotSemantics.OVERLOAD_DEVICE_WORKBENCH_CORE);

    private final OverloadDeviceWorkbenchBlockEntity host;
    private final Slot deviceSlot;
    private final List<Slot> structuralSlots = new ArrayList<>(MAX_STRUCTURAL_SLOTS);
    private final Slot inputSlot;
    private final SimpleContainer inputContainer = new SimpleContainer(1);

    public OverloadDeviceWorkbenchMenu(int id, Inventory playerInventory, OverloadDeviceWorkbenchBlockEntity host) {
        super(TYPE, id, playerInventory, host);
        this.host = host;

        var structuralContainer = new StructuralSlotContainer();
        deviceSlot = addSlot(
                new WorkbenchDeviceSlot(host.getDeviceInventory(), 0, DEVICE_X, DEVICE_Y),
                Ae2ltSlotSemantics.OVERLOAD_DEVICE_WORKBENCH_DEVICE);

        for (int i = 0; i < MAX_STRUCTURAL_SLOTS; i++) {
            var slot = addSlot(
                    new StructuralSlot(
                            structuralContainer,
                            i,
                            STRUCTURAL_X,
                            STRUCTURAL_Y + i * STRUCTURAL_SPACING),
                    STRUCTURAL_SEMANTICS.get(i));
            structuralSlots.add(slot);
        }

        inputSlot = addSlot(
                new ModuleInputSlot(inputContainer, 0, INPUT_X, INPUT_Y),
                Ae2ltSlotSemantics.OVERLOAD_DEVICE_WORKBENCH_MODULE);

        addPlayerInventorySlots(playerInventory);

        registerClientAction("uninstallModuleAtIndex", Integer.class, this::handleUninstallAt);
        registerClientAction("uninstallAllOfIndex", Integer.class, this::handleUninstallAllAt);

        updateSnapshot();
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            tickInstallProgress();
            updateSnapshot();
        }
        super.broadcastChanges();
    }

    @Override
    public void removed(Player player) {
        if (isServerSide()) {
            var residual = inputContainer.removeItemNoUpdate(0);
            if (!residual.isEmpty()) {
                if (!player.getInventory().add(residual)) {
                    player.drop(residual, false);
                }
            }
        }
        super.removed(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (isClientSide() || index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }

        Slot sourceSlot = getSlot(index);
        if (!sourceSlot.hasItem() || !sourceSlot.mayPickup(player)) {
            return ItemStack.EMPTY;
        }

        ItemStack sourceStack = sourceSlot.getItem();
        ItemStack original = sourceStack.copy();
        ItemStack remainder;

        if (isPlayerSideSlot(sourceSlot)) {
            var destinations = getWorkbenchDestinationSlots(sourceStack);
            if (destinations.isEmpty()) {
                return ItemStack.EMPTY;
            }
            remainder = moveIntoSlots(sourceStack.copy(), destinations);
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

    public boolean hasDeviceInserted() {
        return devicePresent != 0;
    }

    public boolean hasCoreInstalled() {
        return coreInstalled != 0;
    }

    public boolean isRailgunDevice() {
        return railgunDevice != 0;
    }

    public List<StructuralSlotSpec> getStructuralSlotSpecs() {
        return host.getStructuralSlots();
    }

    public int getModuleMaxInstallAmount(ItemStack stack) {
        return host.moduleMaxInstallAmount(stack);
    }

    public Component getStatusText() {
        if (!hasDeviceInserted()) {
            return Component.translatable("ae2lt.overload_device_workbench.status.no_device");
        }
        if (!hasCoreInstalled()) {
            return Component.translatable("ae2lt.celestweave.status.missing_core");
        }
        return Component.translatable("ae2lt.overload_device_workbench.status.ready");
    }

    public List<ItemStack> getInstalledModuleList() {
        return host.getModuleList(registryAccess());
    }

    public void requestUninstall(int index, boolean all) {
        sendClientAction(all ? "uninstallAllOfIndex" : "uninstallModuleAtIndex", index);
    }

    private void handleUninstallAt(int index) {
        if (isClientSide()) return;
        var list = host.getModuleList(registryAccess());
        if (index < 0 || index >= list.size()) return;
        var stack = list.get(index);
        if (stack.isEmpty()) return;
        String id = host.moduleTypeId(stack);
        if (id.isBlank()) return;
        var detached = host.uninstallOneModule(registryAccess(), id);
        if (detached.isEmpty()) return;
        giveToPlayer(detached);
    }

    private void handleUninstallAllAt(int index) {
        if (isClientSide()) return;
        var list = host.getModuleList(registryAccess());
        if (index < 0 || index >= list.size()) return;
        var stack = list.get(index);
        if (stack.isEmpty()) return;
        String id = host.moduleTypeId(stack);
        if (id.isBlank()) return;
        var detached = host.uninstallAllOfModule(registryAccess(), id);
        if (detached.isEmpty()) return;
        int max = Math.max(1, detached.getMaxStackSize());
        while (!detached.isEmpty()) {
            var chunk = detached.split(Math.min(max, detached.getCount()));
            giveToPlayer(chunk);
        }
    }

    private void giveToPlayer(ItemStack stack) {
        if (stack.isEmpty()) return;
        var player = getPlayer();
        if (player == null) {
            return;
        }
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private void tickInstallProgress() {
        if (!host.hasInstalledDevice()) {
            installProgress = 0;
            return;
        }
        var stack = inputContainer.getItem(0);
        if (stack.isEmpty() || !host.canInstallOneModule(registryAccess(), stack)) {
            installProgress = 0;
            return;
        }
        installProgress++;
        if (installProgress >= INSTALL_TICKS) {
            installProgress = 0;
            var unit = stack.copyWithCount(1);
            if (host.installOneModule(registryAccess(), unit)) {
                stack.shrink(1);
                inputContainer.setItem(0, stack.isEmpty() ? ItemStack.EMPTY : stack);
            }
        }
    }

    private void updateSnapshot() {
        gridConnected = host.isActive() ? 1 : 0;
        var device = host.getInstalledDevice();
        var adapter = host.currentAdapter();
        devicePresent = adapter == null ? 0 : 1;
        if (adapter == null || device.isEmpty()) {
            moduleTypeCount = 0;
            energyCapacity = 0L;
            coreInstalled = 0;
            energyStored = 0L;
            railgunDevice = 0;
            moduleUnitCount = 0;
            moduleSlotCount = 0;
            return;
        }

        railgunDevice = adapter.deviceKind() == DeviceKind.RAILGUN ? 1 : 0;
        var modules = host.getModuleList(registryAccess());
        moduleTypeCount = modules.size();
        moduleUnitCount = DeviceHubDisplayRules.countModuleUnits(
                modules.stream().map(ItemStack::getCount).toList());
        moduleSlotCount = adapter.deviceKind() == DeviceKind.RAILGUN
                ? 0
                : armorModuleSlotCount(device);
        energyCapacity = adapter.energyBuffer().capacity(device);
        energyStored = adapter.energyBuffer().stored(device);

        coreInstalled = structuralInstalled(DeviceSlotType.CORE) ? 1 : 0;
    }

    private static int armorModuleSlotCount(ItemStack device) {
        return device.getItem() instanceof BaseCelestweaveArmorItem armorItem
                ? armorItem.armorPart().moduleSlotCount()
                : 0;
    }

    private boolean structuralInstalled(DeviceSlotType type) {
        for (var spec : host.getStructuralSlots()) {
            if (spec.slotType() == type
                    && !host.getStructuralSlot(registryAccess(), spec).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void addPlayerInventorySlots(Inventory playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                int slotIndex = column + row * 9 + 9;
                addSlot(new Slot(
                                playerInventory,
                                slotIndex,
                                INVENTORY_X + column * 18,
                                INVENTORY_Y + row * 18),
                        SlotSemantics.PLAYER_INVENTORY);
            }
        }

        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, INVENTORY_X + column * 18, HOTBAR_Y),
                    SlotSemantics.PLAYER_HOTBAR);
        }
    }

    private List<Slot> getWorkbenchDestinationSlots(ItemStack stack) {
        if (stack.getItem() instanceof DeviceItem) {
            return List.of(deviceSlot);
        }
        if (!host.hasInstalledDevice()) {
            return List.of();
        }
        var structural = structuralSlots.stream()
                .filter(slot -> slot.mayPlace(stack))
                .toList();
        if (!structural.isEmpty()) {
            return structural;
        }
        if (host.canInstallOneModule(registryAccess(), stack)) {
            return List.of(inputSlot);
        }
        return List.of();
    }

    private List<Slot> getPlayerDestinationSlots() {
        var result = new ArrayList<Slot>(getSlots(SlotSemantics.PLAYER_INVENTORY));
        result.addAll(getSlots(SlotSemantics.PLAYER_HOTBAR));
        return result;
    }

    private static ItemStack moveIntoSlots(ItemStack stack, List<Slot> destinations) {
        ItemStack remainder = stack;
        for (Slot slot : destinations) {
            if (!slot.hasItem()) {
                continue;
            }
            remainder = slot.safeInsert(remainder);
            if (remainder.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }
        for (Slot slot : destinations) {
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

    private final class StructuralSlotContainer extends SimpleContainer {
        private StructuralSlotContainer() {
            super(MAX_STRUCTURAL_SLOTS);
        }

        @Override
        public ItemStack getItem(int slot) {
            var spec = specForSlot(slot);
            return spec == null ? ItemStack.EMPTY : host.getStructuralSlot(registryAccess(), spec);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            var spec = specForSlot(slot);
            return spec == null ? ItemStack.EMPTY : host.removeStructuralSlot(registryAccess(), spec, amount);
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            var spec = specForSlot(slot);
            return spec == null
                    ? ItemStack.EMPTY
                    : host.removeStructuralSlot(registryAccess(), spec, Integer.MAX_VALUE);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            var spec = specForSlot(slot);
            if (spec != null) {
                host.setStructuralSlot(registryAccess(), spec, stack);
            }
        }

        @Override
        public boolean stillValid(Player player) {
            return OverloadDeviceWorkbenchMenu.this.stillValid(player);
        }
    }

    private static final class WorkbenchDeviceSlot extends AppEngSlot {
        private WorkbenchDeviceSlot(InternalInventory inventory, int invSlot, int x, int y) {
            super(inventory, invSlot);
            this.x = x;
            this.y = y;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    private final class StructuralSlot extends Slot {
        private StructuralSlot(StructuralSlotContainer container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            var spec = specForSlot(getContainerSlot());
            return spec != null && host.canPlaceStructural(registryAccess(), spec, stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            var spec = specForSlot(getContainerSlot());
            return spec != null
                    && host.mayPickupStructural(registryAccess(), spec, player, getCarried())
                    && super.mayPickup(player);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    private final class ModuleInputSlot extends Slot {
        private ModuleInputSlot(SimpleContainer container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return host.hasInstalledDevice()
                    && !stack.isEmpty()
                    && host.canInstallOneModule(registryAccess(), stack);
        }

        @Override
        public int getMaxStackSize() {
            return 64;
        }
    }

    private StructuralSlotSpec specForSlot(int slot) {
        var specs = host.getStructuralSlots();
        return slot >= 0 && slot < specs.size() ? specs.get(slot) : null;
    }
}
