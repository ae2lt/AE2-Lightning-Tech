package com.moakiee.ae2lt.menu.hub;

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
import com.moakiee.ae2lt.item.railgun.ElectromagneticRailgunItem;
import com.moakiee.ae2lt.item.railgun.RailgunSettings;
import com.moakiee.ae2lt.network.hub.DeviceHubSyncPacket;
import com.moakiee.ae2lt.celestweave.BaseCelestweaveArmorItem;
import com.moakiee.ae2lt.celestweave.CelestweaveArmorState;
import com.moakiee.ae2lt.celestweave.module.FlightSpeedOption;
import com.moakiee.ae2lt.registry.ModDataComponents;

/**
 * Unified device hub menu — no item slots, pure status viewer + configuration.
 * <p>
 * Tab selection syncs via {@link ContainerData}. Full device status syncs via
 * {@link DeviceHubSyncPacket}, because vanilla menu data is short-sized on the wire.
 */
public class DeviceHubMenu extends AbstractContainerMenu {

    // ── Data slot indices ──
    public static final int DATA_SELECTED_TAB = 0;
    public static final int DATA_TAB_AVAILABILITY = 1;
    public static final int DATA_COUNT = 2;

    public static final int TAB_HELMET = 0;
    public static final int TAB_CHESTPLATE = 1;
    public static final int TAB_LEGGINGS = 2;
    public static final int TAB_BOOTS = 3;
    public static final int TAB_RAILGUN = 4;
    public static final int TAB_COUNT = 5;

    public static final MenuType<DeviceHubMenu> TYPE = IMenuTypeExtension.create(DeviceHubMenu::new);

    public final ContainerData data;

    // ── Client-side synced status data ──
    private String deviceName = "";
    private boolean hasCore;
    private boolean powered;
    private boolean terrainDestruction;
    private boolean pvp;
    private boolean soundEnabled;
    private List<String> moduleNameKeys = List.of();
    private List<Integer> moduleCounts = List.of();
    private List<Boolean> moduleEnabled = List.of();
    private int selectedModuleIndex = -1;
    private List<String> moduleConfigKeys = List.of();
    private List<String> moduleConfigLabels = List.of();
    private List<String> moduleConfigValues = List.of();
    private List<Boolean> moduleConfigEditable = List.of();

    // ── Server-side state ──
    private int selectedTab;
    private int lastSyncedTab = -1;
    @Nullable
    private DeviceHubSyncPacket lastSyncPacket;

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
            status = DeviceStatusModel.fromArmorStack(deviceStack, serverPlayer, selectedModuleIndex);
        }
        selectedModuleIndex = status.selectedModuleIndex();
        // Update ContainerData
        ServerData sd = (ServerData) data;
        sd.values[DATA_SELECTED_TAB] = selectedTab;
        sd.values[DATA_TAB_AVAILABILITY] = tabMask;

        super.broadcastChanges();

        DeviceHubSyncPacket syncPacket = toSyncPacket(status);
        if (selectedTab != lastSyncedTab || !syncPacket.equals(lastSyncPacket)) {
            lastSyncedTab = selectedTab;
            lastSyncPacket = syncPacket;
            PacketDistributor.sendToPlayer(serverPlayer, syncPacket);
        }
    }

    private DeviceHubSyncPacket toSyncPacket(DeviceStatusModel status) {
        List<String> nameKeys = status.modules().stream().map(DeviceStatusModel.ModuleInfo::nameKey).toList();
        List<Integer> counts = status.modules().stream().map(DeviceStatusModel.ModuleInfo::count).toList();
        List<Boolean> enabled = status.modules().stream().map(DeviceStatusModel.ModuleInfo::enabled).toList();
        List<String> moduleConfigKeys = status.moduleConfigs().stream().map(DeviceStatusModel.ModuleConfigInfo::key).toList();
        List<String> moduleConfigLabels = status.moduleConfigs().stream().map(DeviceStatusModel.ModuleConfigInfo::label).toList();
        List<String> moduleConfigValues = status.moduleConfigs().stream().map(DeviceStatusModel.ModuleConfigInfo::value).toList();
        List<Boolean> moduleConfigEditable = status.moduleConfigs().stream().map(DeviceStatusModel.ModuleConfigInfo::editable).toList();
        return new DeviceHubSyncPacket(
                containerId,
                status.displayName(),
                status.hasCore(),
                status.powered(),
                status.terrainDestruction(),
                status.pvp(),
                status.soundEnabled(),
                nameKeys,
                counts,
                enabled,
                status.selectedModuleIndex(),
                moduleConfigKeys,
                moduleConfigLabels,
                moduleConfigValues,
                moduleConfigEditable);
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
            boolean hasCore,
            boolean powered,
            boolean terrainDestruction,
            boolean pvp,
            boolean soundEnabled,
            List<String> nameKeys,
            List<Integer> counts,
            List<Boolean> enabled,
            int selectedModuleIndex,
            List<String> moduleConfigKeys,
            List<String> moduleConfigLabels,
            List<String> moduleConfigValues,
            List<Boolean> moduleConfigEditable) {
        this.deviceName = name;
        this.hasCore = hasCore;
        this.powered = powered;
        this.terrainDestruction = terrainDestruction;
        this.pvp = pvp;
        this.soundEnabled = soundEnabled;
        this.moduleNameKeys = List.copyOf(nameKeys);
        this.moduleCounts = List.copyOf(counts);
        this.moduleEnabled = List.copyOf(enabled);
        this.selectedModuleIndex = selectedModuleIndex;
        this.moduleConfigKeys = List.copyOf(moduleConfigKeys);
        this.moduleConfigLabels = List.copyOf(moduleConfigLabels);
        this.moduleConfigValues = List.copyOf(moduleConfigValues);
        this.moduleConfigEditable = List.copyOf(moduleConfigEditable);
    }

    // ── Client-side accessors ──
    public int getSelectedTab() {
        return data.get(DATA_SELECTED_TAB);
    }

    public int getTabAvailability() {
        return data.get(DATA_TAB_AVAILABILITY);
    }

    public String getDeviceName() {
        return deviceName;
    }

    public List<String> getModuleNameKeys() {
        return moduleNameKeys;
    }

    public List<Integer> getModuleCounts() {
        return moduleCounts;
    }

    public List<Boolean> getModuleEnabled() {
        return moduleEnabled;
    }

    public boolean hasCore() {
        return hasCore;
    }

    public boolean isPowered() {
        return powered;
    }

    public boolean isTerrainDestruction() {
        return terrainDestruction;
    }

    public boolean isPvp() {
        return pvp;
    }

    public boolean isSoundEnabled() {
        return soundEnabled;
    }

    public int getSelectedModuleIndex() {
        return selectedModuleIndex;
    }

    public List<String> getModuleConfigKeys() {
        return moduleConfigKeys;
    }

    public List<String> getModuleConfigLabels() {
        return moduleConfigLabels;
    }

    public List<String> getModuleConfigValues() {
        return moduleConfigValues;
    }

    public List<Boolean> getModuleConfigEditable() {
        return moduleConfigEditable;
    }

    // ── Server-side actions ──
    public void selectTab(int tab) {
        if (tab >= 0 && tab < TAB_COUNT) {
            if (this.selectedTab != tab) {
                this.selectedModuleIndex = -1;
            }
            this.selectedTab = tab;
        }
    }

    public void selectModule(int moduleIndex) {
        if (moduleIndex >= 0) {
            this.selectedModuleIndex = moduleIndex;
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
        var submodules = CelestweaveArmorState.collectSubmodules(deviceStack, player.registryAccess());
        if (moduleIndex < 0 || moduleIndex >= submodules.size()) return;
        var sub = submodules.get(moduleIndex);
        boolean current = CelestweaveArmorState.isSubmoduleEnabled(deviceStack, sub);
        CelestweaveArmorState.setSubmoduleEnabled(deviceStack, sub, !current);
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
        railgun.set(ModDataComponents.RAILGUN_SETTINGS.get(), s.withPvp(!s.pvp()));
    }

    public void toggleRailgunSound() {
        if (!(getPlayer() instanceof ServerPlayer player)) return;
        ItemStack railgun = findDevice(player, TAB_RAILGUN);
        if (railgun.isEmpty()) return;
        RailgunSettings s = railgun.getOrDefault(ModDataComponents.RAILGUN_SETTINGS.get(), RailgunSettings.DEFAULT);
        railgun.set(ModDataComponents.RAILGUN_SETTINGS.get(), s.withSound(!s.soundEnabled()));
    }

    public void cycleSelectedModuleConfig(int optionIndex) {
        if (!(getPlayer() instanceof ServerPlayer player)) return;
        if (selectedTab == TAB_RAILGUN) return;
        ItemStack deviceStack = findDevice(player, selectedTab);
        if (deviceStack.isEmpty()) return;
        var submodules = CelestweaveArmorState.collectSubmodules(deviceStack, player.registryAccess());
        if (selectedModuleIndex < 0 || selectedModuleIndex >= submodules.size()) return;
        var submodule = submodules.get(selectedModuleIndex);
        var configs = submodule.getConfigs(deviceStack);
        if (optionIndex < 0 || optionIndex >= configs.size()) return;
        var config = configs.get(optionIndex);
        var next = config.nextValue();
        if (next == null) return;
        submodule.setConfig(deviceStack, config.key(), next);
        // sync inertia to client when flight module config changes
        if ("flight_inertia".equals(config.key())
                || FlightSpeedOption.CONFIG_KEY.equals(config.key())) {
            CelestweaveArmorState.syncFlightInertiaToClientIfFlight(player, deviceStack);
        }
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
        return stack.getItem() instanceof BaseCelestweaveArmorItem ? stack : ItemStack.EMPTY;
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
