package com.moakiee.ae2lt.menu.hub;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.network.PacketDistributor;

import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.device.DeviceKind;
import com.moakiee.ae2lt.item.railgun.ElectromagneticRailgunItem;
import com.moakiee.ae2lt.item.railgun.RailgunSettings;
import com.moakiee.ae2lt.network.hub.DeviceHubSyncPacket;
import com.moakiee.ae2lt.overload.armor.BaseOverloadArmorItem;
import com.moakiee.ae2lt.overload.armor.OverloadArmorState;
import com.moakiee.ae2lt.registry.ModDataComponents;

/**
 * Unified device hub menu — no item slots, pure status viewer + configuration.
 * <p>
 * Numeric data syncs via {@link ContainerData}. String data (module names, device
 * name, bound dimension) syncs via {@link DeviceHubSyncPacket}.
 */
public class DeviceHubMenu extends AbstractContainerMenu {

    // ── Data slot indices ──
    public static final int DATA_SELECTED_TAB = 0;
    public static final int DATA_TAB_AVAILABILITY = 1;
    public static final int DATA_ENERGY_STORED_HI = 2;
    public static final int DATA_ENERGY_STORED_LO = 3;
    public static final int DATA_ENERGY_CAPACITY_HI = 4;
    public static final int DATA_ENERGY_CAPACITY_LO = 5;
    public static final int DATA_DYNAMIC_LOAD = 6;
    public static final int DATA_OVERLOAD_CAP = 7;
    public static final int DATA_LOCK_STATE = 8;
    public static final int DATA_LOCK_VALUE = 9;
    public static final int DATA_POWERED = 10;
    public static final int DATA_GRID_REACHABLE = 11;
    public static final int DATA_APP_FLUX_ONLINE = 12;
    public static final int DATA_MODULE_COUNT = 13;
    public static final int DATA_MODULE_SLOT_COUNT = 14;
    public static final int DATA_MODULE_MASK = 15;
    public static final int DATA_RAILGUN_TERRAIN = 16;
    public static final int DATA_RAILGUN_PVP = 17;
    public static final int DATA_RAILGUN_TERRAIN_ALLOWED = 18;
    public static final int DATA_COUNT = 19;

    public static final int TAB_HELMET = 0;
    public static final int TAB_CHESTPLATE = 1;
    public static final int TAB_LEGGINGS = 2;
    public static final int TAB_BOOTS = 3;
    public static final int TAB_RAILGUN = 4;
    public static final int TAB_COUNT = 5;

    public static final MenuType<DeviceHubMenu> TYPE = IMenuTypeExtension.create(DeviceHubMenu::new);

    public final ContainerData data;

    // ── Client-side synced string data ──
    private String deviceName = "";
    private String boundDim = "";
    private List<String> moduleIds = List.of();
    private List<String> moduleNameKeys = List.of();
    private List<Integer> moduleCounts = List.of();
    private List<Integer> moduleLoads = List.of();

    // ── Server-side state ──
    private int selectedTab;
    @Nullable
    private DeviceStatusModel lastStatus;
    private List<String> lastModuleNameKeys = List.of();
    private List<Integer> lastModuleCounts = List.of();
    private List<Integer> lastModuleLoads = List.of();

    // ── Client constructor ──
    public DeviceHubMenu(int containerId, Inventory inv, FriendlyByteBuf buf) {
        super(TYPE, containerId);
        this.selectedTab = buf.readVarInt();
        this.data = new SimpleContainerData(DATA_COUNT);
        this.data.set(DATA_SELECTED_TAB, selectedTab);
        addDataSlots(data);
    }

    // ── Server constructor ──
    public DeviceHubMenu(int containerId, Inventory inv, int defaultTab) {
        super(TYPE, containerId);
        this.selectedTab = defaultTab;
        this.trackedPlayer = inv.player;
        this.data = new ServerData();
        addDataSlots(data);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    // ── Server-side: periodic sync ──
    @Override
    public void broadcastChanges() {
        if (!(getPlayer() instanceof ServerPlayer serverPlayer)) {
            super.broadcastChanges();
            return;
        }

        // Scan devices and update tab availability
        int tabMask = 0;
        for (int t = 0; t < TAB_COUNT; t++) {
            if (!findDevice(serverPlayer, t).isEmpty()) {
                tabMask |= (1 << t);
            }
        }

        // If selected tab has no device, find first available
        if ((tabMask & (1 << selectedTab)) == 0) {
            for (int t = 0; t < TAB_COUNT; t++) {
                if ((tabMask & (1 << t)) != 0) {
                    selectedTab = t;
                    break;
                }
            }
        }

        // Build status for current tab
        ItemStack deviceStack = findDevice(serverPlayer, selectedTab);
        DeviceStatusModel status;
        if (deviceStack.isEmpty()) {
            status = DeviceStatusModel.EMPTY;
        } else if (selectedTab == TAB_RAILGUN) {
            status = DeviceStatusModel.fromRailgunStack(deviceStack, serverPlayer);
        } else {
            status = DeviceStatusModel.fromArmorStack(deviceStack, serverPlayer);
        }
        lastStatus = status;

        // Update ContainerData
        ServerData sd = (ServerData) data;
        sd.values[DATA_SELECTED_TAB] = selectedTab;
        sd.values[DATA_TAB_AVAILABILITY] = tabMask;
        sd.values[DATA_ENERGY_STORED_HI] = (int) (status.storedFe() >> 32);
        sd.values[DATA_ENERGY_STORED_LO] = (int) status.storedFe();
        sd.values[DATA_ENERGY_CAPACITY_HI] = (int) (status.capacityFe() >> 32);
        sd.values[DATA_ENERGY_CAPACITY_LO] = (int) status.capacityFe();
        sd.values[DATA_DYNAMIC_LOAD] = status.dynamicLoad();
        sd.values[DATA_OVERLOAD_CAP] = status.overloadCap();
        sd.values[DATA_LOCK_STATE] = status.lockState();
        sd.values[DATA_LOCK_VALUE] = status.lockValue();
        sd.values[DATA_POWERED] = status.powered() ? 1 : 0;
        sd.values[DATA_GRID_REACHABLE] = status.gridReachable() ? 1 : 0;
        sd.values[DATA_APP_FLUX_ONLINE] = status.appFluxOnline() ? 1 : 0;
        sd.values[DATA_MODULE_COUNT] = status.modules().size();
        sd.values[DATA_MODULE_SLOT_COUNT] = status.moduleSlotCount();
        int moduleMask = 0;
        for (int i = 0; i < status.modules().size() && i < 32; i++) {
            if (status.modules().get(i).enabled()) {
                moduleMask |= (1 << i);
            }
        }
        sd.values[DATA_MODULE_MASK] = moduleMask;
        sd.values[DATA_RAILGUN_TERRAIN] = status.terrainDestruction() ? 1 : 0;
        sd.values[DATA_RAILGUN_PVP] = status.pvpLock() ? 1 : 0;
        sd.values[DATA_RAILGUN_TERRAIN_ALLOWED] = status.terrainDestructionAllowed() ? 1 : 0;

        super.broadcastChanges();

        // Sync string data if changed
        List<String> currentNameKeys = status.modules().stream().map(DeviceStatusModel.ModuleInfo::nameKey).toList();
        List<Integer> currentCounts = status.modules().stream().map(DeviceStatusModel.ModuleInfo::count).toList();
        List<Integer> currentLoads = status.modules().stream().map(DeviceStatusModel.ModuleInfo::load).toList();
        List<String> currentIds = status.modules().stream().map(DeviceStatusModel.ModuleInfo::id).toList();
        if (!currentNameKeys.equals(lastModuleNameKeys)
                || !currentCounts.equals(lastModuleCounts)
                || !currentLoads.equals(lastModuleLoads)
                || !status.displayName().equals(deviceName)) {
            lastModuleNameKeys = currentNameKeys;
            lastModuleCounts = currentCounts;
            lastModuleLoads = currentLoads;
            PacketDistributor.sendToPlayer(serverPlayer,
                    new DeviceHubSyncPacket(
                            containerId,
                            status.displayName(),
                            status.boundDim(),
                            currentIds,
                            currentNameKeys,
                            currentCounts,
                            currentLoads));
        }
    }

    @Nullable
    private Player getPlayer() {
        // AbstractContainerMenu doesn't store player directly;
        // we rely on the fact that broadcastChanges is called with a valid player context.
        // We find the player from the slots if any, but since we have none, we track it.
        return trackedPlayer;
    }

    private Player trackedPlayer;

    @Override
    public void initializeContents(int stateId, List<ItemStack> items, ItemStack carried) {
        super.initializeContents(stateId, items, carried);
    }

    // Override to capture the player reference
    public void setPlayer(Player player) {
        this.trackedPlayer = player;
    }

    // ── Client-side: receive sync packet ──
    public void receiveSync(
            String name,
            String dim,
            List<String> ids,
            List<String> nameKeys,
            List<Integer> counts,
            List<Integer> loads) {
        this.deviceName = name;
        this.boundDim = dim;
        this.moduleIds = ids;
        this.moduleNameKeys = nameKeys;
        this.moduleCounts = counts;
        this.moduleLoads = loads;
    }

    // ── Client-side accessors ──
    public int getSelectedTab() {
        return data.get(DATA_SELECTED_TAB);
    }

    public int getTabAvailability() {
        return data.get(DATA_TAB_AVAILABILITY);
    }

    public long getEnergyStored() {
        return ((long) data.get(DATA_ENERGY_STORED_HI) << 32) | (data.get(DATA_ENERGY_STORED_LO) & 0xFFFFFFFFL);
    }

    public long getEnergyCapacity() {
        return ((long) data.get(DATA_ENERGY_CAPACITY_HI) << 32) | (data.get(DATA_ENERGY_CAPACITY_LO) & 0xFFFFFFFFL);
    }

    public String getDeviceName() {
        return deviceName;
    }

    public String getBoundDim() {
        return boundDim;
    }

    public List<String> getModuleIds() {
        return moduleIds;
    }

    public List<String> getModuleNames() {
        return moduleNameKeys;
    }

    public List<String> getModuleNameKeys() {
        return moduleNameKeys;
    }

    public List<Integer> getModuleCounts() {
        return moduleCounts;
    }

    public List<Integer> getModuleLoads() {
        return moduleLoads;
    }

    // ── Server-side actions ──
    public void selectTab(int tab) {
        if (tab >= 0 && tab < TAB_COUNT) {
            this.selectedTab = tab;
        }
    }

    public void toggleModule(int moduleIndex) {
        if (!(getPlayer() instanceof ServerPlayer player)) return;
        ItemStack deviceStack = findDevice(player, selectedTab);
        if (deviceStack.isEmpty()) return;

        if (selectedTab == TAB_RAILGUN) {
            // Railgun modules are not toggleable
            return;
        }
        // Armor: toggle submodule
        var submodules = OverloadArmorState.collectSubmodules(deviceStack, player.registryAccess());
        if (moduleIndex < 0 || moduleIndex >= submodules.size()) return;
        var sub = submodules.get(moduleIndex);
        boolean current = OverloadArmorState.isSubmoduleEnabled(deviceStack, sub);
        OverloadArmorState.setSubmoduleEnabled(deviceStack, sub, !current);
    }

    public void toggleRailgunTerrain() {
        if (!(getPlayer() instanceof ServerPlayer player)) return;
        if (!AE2LTCommonConfig.railgunTerrainDestructionEnabled()) return;
        ItemStack railgun = findDevice(player, TAB_RAILGUN);
        if (railgun.isEmpty()) return;
        RailgunSettings s = railgun.getOrDefault(ModDataComponents.RAILGUN_SETTINGS.get(), RailgunSettings.DEFAULT);
        railgun.set(ModDataComponents.RAILGUN_SETTINGS.get(), s.withTerrain(!s.terrainDestruction()));
    }

    public void toggleRailgunPvp() {
        if (!(getPlayer() instanceof ServerPlayer player)) return;
        ItemStack railgun = findDevice(player, TAB_RAILGUN);
        if (railgun.isEmpty()) return;
        RailgunSettings s = railgun.getOrDefault(ModDataComponents.RAILGUN_SETTINGS.get(), RailgunSettings.DEFAULT);
        railgun.set(ModDataComponents.RAILGUN_SETTINGS.get(), s.withPvpLock(!s.pvpLock()));
    }

    // ── Device lookup ──
    private static ItemStack findDevice(Player player, int tab) {
        return switch (tab) {
            case TAB_HELMET -> findArmor(player, EquipmentSlot.HEAD);
            case TAB_CHESTPLATE -> findArmor(player, EquipmentSlot.CHEST);
            case TAB_LEGGINGS -> findArmor(player, EquipmentSlot.LEGS);
            case TAB_BOOTS -> findArmor(player, EquipmentSlot.FEET);
            case TAB_RAILGUN -> findRailgun(player);
            default -> ItemStack.EMPTY;
        };
    }

    private static ItemStack findArmor(Player player, EquipmentSlot slot) {
        ItemStack stack = player.getItemBySlot(slot);
        return stack.getItem() instanceof BaseOverloadArmorItem ? stack : ItemStack.EMPTY;
    }

    private static ItemStack findRailgun(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof ElectromagneticRailgunItem) return main;
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof ElectromagneticRailgunItem) return off;
        return ItemStack.EMPTY;
    }

    // ── Server-side ContainerData impl ──
    private static class ServerData implements ContainerData {
        final int[] values = new int[DATA_COUNT];

        @Override
        public int get(int index) {
            return index >= 0 && index < values.length ? values[index] : 0;
        }

        @Override
        public void set(int index, int value) {
            if (index >= 0 && index < values.length) {
                values[index] = value;
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    }
}
