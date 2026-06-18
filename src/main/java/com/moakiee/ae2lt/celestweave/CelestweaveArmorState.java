package com.moakiee.ae2lt.celestweave;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.network.PacketDistributor;

import com.moakiee.ae2lt.celestweave.ArmorEnergyModuleItem;
import com.moakiee.ae2lt.network.CelestweaveSubmoduleActivePacket;
import com.moakiee.ae2lt.network.FlightInertiaSyncPacket;
import com.moakiee.ae2lt.celestweave.module.FlightSubmodule;
import com.moakiee.ae2lt.celestweave.module.PhaseFlightSubmodule;
import com.moakiee.ae2lt.celestweave.module.CelestweaveArmorSubmodule;
import com.moakiee.ae2lt.celestweave.module.CelestweaveArmorSubmoduleItem;
import com.moakiee.ae2lt.registry.ModItems;
import com.moakiee.ae2lt.celestweave.state.ArmorPersistentData;
import com.moakiee.ae2lt.celestweave.state.ArmorRuntimeRegistry;

public final class CelestweaveArmorState {
    public static final int SLOT_CORE = 0;
    public static final int SLOT_COUNT = 1;
    private static final int MAX_MODULE_TYPES = 32;
    private static volatile boolean CLIENT_FLIGHT_INERTIA = true;
    private static volatile UUID CLIENT_FLIGHT_INERTIA_ARMOR_ID = null;

    private CelestweaveArmorState() {
    }

    public static UUID ensureArmorId(ItemStack armor) {
        return ArmorPersistentData.ensureArmorId(armor);
    }

    @Nullable
    public static UUID getArmorId(ItemStack armor) {
        return ArmorPersistentData.armorId(armor).orElse(null);
    }

    public static long getCachedEnergyModuleCapacityFe(ItemStack armor) {
        return ArmorPersistentData.getCachedEnergyModuleCapacityFe(armor);
    }

    public static boolean hasCachedEnergyModuleCapacityFe(ItemStack armor) {
        return ArmorPersistentData.hasCachedEnergyModuleCapacityFe(armor);
    }

    public static void setCachedEnergyModuleCapacityFe(ItemStack armor, long capacityFe) {
        ArmorPersistentData.setCachedEnergyModuleCapacityFe(armor, capacityFe);
    }

    public static ItemStack getSlot(ItemStack armor, HolderLookup.Provider registries, int slot) {
        if (slot == SLOT_CORE) {
            return ArmorPersistentData.structuralCore(armor);
        }
        return ItemStack.EMPTY;
    }

    public static void setSlot(ItemStack armor, HolderLookup.Provider registries, int slot, ItemStack stack) {
        if (slot == SLOT_CORE) {
            ArmorPersistentData.setStructuralCore(armor, stack);
        }
    }

    public static boolean canInstallCore(ItemStack armor, HolderLookup.Provider registries, ItemStack core) {
        return core != null && !core.isEmpty() && core.is(ModItems.ULTIMATE_OVERLOAD_CORE.get());
    }

    public static boolean hasCore(ItemStack armor, HolderLookup.Provider registries) {
        return ArmorPersistentData.hasStructuralCore(armor);
    }

    public static ArmorPart armorPart(ItemStack armor) {
        if (armor != null && armor.getItem() instanceof BaseCelestweaveArmorItem item) {
            return item.armorPart();
        }
        return ArmorPart.CHEST;
    }

    public static boolean canInstallModule(ItemStack armor, HolderLookup.Provider registries, ItemStack candidate) {
        if (candidate == null || candidate.isEmpty()) {
            return false;
        }
        if (candidate.getItem() instanceof ArmorEnergyModuleItem energyModule) {
            ArmorPart part = armorPart(armor);
            if (!energyModule.acceptableDevices().contains(part.deviceKind())
                    || ArmorEnergyModuleItem.acceptableSlotFor(part.deviceKind()) != part.moduleSlot()) {
                return false;
            }
            if (getInstalledAmount(armor, registries, ArmorEnergyModuleItem.MODULE_TYPE_ID) >= 1) {
                return false;
            }
            return getInstalledUnitCount(armor, registries) < part.moduleSlotCount();
        }
        if (!(candidate.getItem() instanceof CelestweaveArmorSubmoduleItem provider)) {
            return false;
        }
        ArmorPart part = armorPart(armor);
        if (provider.armorPart() != part || provider.acceptableSlot() != part.moduleSlot()) {
            return false;
        }
        String id = resolveSubmoduleId(candidate);
        if (id.isBlank()) {
            return false;
        }
        String groupId = resolveSubmoduleGroupId(candidate);
        if (!groupId.isBlank()) {
            int installedInGroup = getInstalledGroupAmount(armor, registries, groupId);
            int installedSameId = getInstalledAmount(armor, registries, id);
            if (installedInGroup > installedSameId) {
                return false;
            }
        }
        int current = getInstalledAmount(armor, registries, id);
        int max = getSubmoduleMaxInstallAmountForStack(candidate);
        if (max > 0 && current >= max) {
            return false;
        }
        if (getInstalledUnitCount(armor, registries) >= part.moduleSlotCount()) {
            return false;
        }
        if (current == 0 && loadModuleStacks(armor, registries).size() >= MAX_MODULE_TYPES) {
            return false;
        }
        return true;
    }

    public static boolean installOneModule(ItemStack armor, HolderLookup.Provider registries, ItemStack candidate) {
        if (!canInstallModule(armor, registries, candidate)) {
            return false;
        }
        String id = resolveSubmoduleId(candidate);
        var stacks = new ArrayList<>(loadModuleStacks(armor, registries));
        boolean merged = false;
        for (var stack : stacks) {
            if (id.equals(resolveSubmoduleId(stack))) {
                stack.grow(1);
                merged = true;
                break;
            }
        }
        if (!merged) {
            stacks.add(candidate.copyWithCount(1));
        }
        saveModuleStacks(armor, registries, stacks);
        return true;
    }

    public static ItemStack uninstallOneModule(ItemStack armor, HolderLookup.Provider registries, String submoduleId) {
        if (submoduleId == null || submoduleId.isBlank()) {
            return ItemStack.EMPTY;
        }
        var stacks = new ArrayList<>(loadModuleStacks(armor, registries));
        for (int index = 0; index < stacks.size(); index++) {
            var stack = stacks.get(index);
            if (!submoduleId.equals(resolveSubmoduleId(stack))) {
                continue;
            }
            ItemStack detached = stack.copyWithCount(1);
            if (stack.getCount() <= 1) {
                stacks.remove(index);
            } else {
                stack.shrink(1);
            }
            saveModuleStacks(armor, registries, stacks);
            pruneRemovedRuntime(getArmorId(armor), installedSubmoduleIds(armor, registries));
            return detached;
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack uninstallAllOfType(ItemStack armor, HolderLookup.Provider registries, String submoduleId) {
        if (submoduleId == null || submoduleId.isBlank()) {
            return ItemStack.EMPTY;
        }
        var stacks = new ArrayList<>(loadModuleStacks(armor, registries));
        for (int index = 0; index < stacks.size(); index++) {
            var stack = stacks.get(index);
            if (!submoduleId.equals(resolveSubmoduleId(stack))) {
                continue;
            }
            stacks.remove(index);
            saveModuleStacks(armor, registries, stacks);
            pruneRemovedRuntime(getArmorId(armor), installedSubmoduleIds(armor, registries));
            return stack.copy();
        }
        return ItemStack.EMPTY;
    }

    public static List<ItemStack> loadModuleStacks(ItemStack armor, HolderLookup.Provider registries) {
        return ArmorPersistentData.loadModuleStacks(armor, registries);
    }

    private static void saveModuleStacks(ItemStack armor, HolderLookup.Provider registries, List<ItemStack> stacks) {
        ArmorPersistentData.saveModuleStacks(armor, registries, stacks);
    }

    public static boolean hasAnyInstalledModule(ItemStack armor, HolderLookup.Provider registries) {
        return !loadModuleStacks(armor, registries).isEmpty();
    }

    public static int getInstalledAmount(ItemStack armor, HolderLookup.Provider registries, String submoduleId) {
        if (submoduleId == null || submoduleId.isBlank()) {
            return 0;
        }
        for (var stack : loadModuleStacks(armor, registries)) {
            if (submoduleId.equals(resolveSubmoduleId(stack))) {
                return stack.getCount();
            }
        }
        return 0;
    }

    private static int getInstalledGroupAmount(ItemStack armor, HolderLookup.Provider registries, String groupId) {
        if (groupId == null || groupId.isBlank()) {
            return 0;
        }
        int total = 0;
        for (var stack : loadModuleStacks(armor, registries)) {
            if (groupId.equals(resolveSubmoduleGroupId(stack))) {
                total += Math.max(1, stack.getCount());
            }
        }
        return total;
    }

    private static int getInstalledUnitCount(ItemStack armor, HolderLookup.Provider registries) {
        int total = 0;
        for (var stack : loadModuleStacks(armor, registries)) {
            total += Math.max(1, stack.getCount());
        }
        return total;
    }

    public static List<CelestweaveArmorSubmodule> collectSubmodules(ItemStack armor, HolderLookup.Provider registries) {
        return collectInstalledSubmoduleEntries(armor, registries).stream()
                .map(InstalledSubmodule::submodule)
                .toList();
    }

    public static String moduleTypeId(ItemStack stack) {
        return resolveSubmoduleId(stack);
    }

    public static int getSubmoduleMaxInstallAmountForStack(ItemStack stack) {
        if (stack != null && stack.getItem() instanceof ArmorEnergyModuleItem) {
            return 1;
        }
        int max = 0;
        if (stack != null && stack.getItem() instanceof CelestweaveArmorSubmoduleItem provider) {
            var values = new ArrayList<Integer>();
            provider.collectSubmodules(stack, submodule -> values.add(Math.max(0, submodule.getMaxInstallAmount())));
            for (int value : values) {
                if (value > 0) {
                    max = max == 0 ? value : Math.min(max, value);
                }
            }
        }
        return max;
    }

    public static int getSubmoduleMaxInstallAmount(CelestweaveArmorSubmodule submodule) {
        return submodule == null ? 0 : Math.max(0, submodule.getMaxInstallAmount());
    }

    public static boolean isSubmoduleInstalled(ItemStack armor, HolderLookup.Provider registries, String submoduleId) {
        return getInstalledAmount(armor, registries, submoduleId) > 0;
    }

    public static boolean isSubmoduleInstalled(ItemStack armor, String submoduleId) {
        return ArmorPersistentData.hasInstalledSubmodule(armor, submoduleId);
    }

    public static boolean isSubmoduleEnabled(ItemStack armor, CelestweaveArmorSubmodule submodule) {
        return submodule != null && isSubmoduleEnabled(armor, submodule.id(), submodule.defaultEnabled());
    }

    public static boolean isSubmoduleEnabled(ItemStack armor, String submoduleId, boolean defaultEnabled) {
        return ArmorPersistentData.getToggle(armor, submoduleId, defaultEnabled);
    }

    public static void setSubmoduleEnabled(ItemStack armor, CelestweaveArmorSubmodule submodule, boolean enabled) {
        if (submodule != null) {
            setSubmoduleEnabled(armor, submodule.id(), enabled, submodule.defaultEnabled());
        }
    }

    public static void setSubmoduleEnabled(ItemStack armor, String submoduleId, boolean enabled, boolean defaultEnabled) {
        ArmorPersistentData.setToggle(armor, submoduleId, enabled, defaultEnabled);
    }

    public static int buildSubmoduleMask(ItemStack armor, List<CelestweaveArmorSubmodule> submodules) {
        int mask = 0;
        int limit = Math.min(submodules.size(), Integer.SIZE - 1);
        for (int i = 0; i < limit; i++) {
            if (isSubmoduleEnabled(armor, submodules.get(i))) {
                mask |= 1 << i;
            }
        }
        return mask;
    }

    public static CompoundTag getSubmoduleData(ItemStack armor, CelestweaveArmorSubmodule submodule) {
        return submodule == null ? new CompoundTag() : getStoredSubmoduleData(armor, submodule.id());
    }

    public static void setSubmoduleData(ItemStack armor, CelestweaveArmorSubmodule submodule, CompoundTag data) {
        if (submodule != null) {
            setStoredSubmoduleData(armor, submodule.id(), data);
        }
    }

    public static boolean isSubmoduleRuntimeActive(ItemStack armor, String submoduleId) {
        UUID id = getArmorId(armor);
        return id != null && ArmorRuntimeRegistry.isSubmoduleRuntimeActive(id, submoduleId);
    }

    public static boolean isSubmoduleActive(ItemStack armor, CelestweaveArmorSubmodule submodule, HolderLookup.Provider registries, boolean equipped) {
        return submodule != null && isSubmoduleRuntimeActive(armor, submodule.id());
    }

    public static void syncSubmoduleActiveState(
            @Nullable Player player,
            ItemStack armor,
            HolderLookup.Provider registries,
            boolean equipped,
            Dist dist) {
        syncSubmoduleActiveState(player, armor, collectInstalledSubmoduleEntries(armor, registries), equipped, dist);
    }

    public static void syncSubmoduleActiveState(
            @Nullable Player player,
            ItemStack armor,
            List<InstalledSubmodule> installedSubmodules,
            boolean equipped,
            Dist dist) {
        UUID armorId = ensureArmorId(armor);
        boolean hasCore = equipped && ArmorPersistentData.hasStructuralCore(armor);
        for (var entry : installedSubmodules) {
            var submodule = entry.submodule();
            boolean active = hasCore && isSubmoduleEnabled(armor, submodule);
            Boolean previous = dist == Dist.CLIENT
                    ? ArmorRuntimeRegistry.setClientSubmoduleActive(armorId, submodule.id(), active)
                    : ArmorRuntimeRegistry.setServerSubmoduleActive(armorId, submodule.id(), active);
            setSubmoduleRuntimeActive(armor, submodule.id(), active);
            boolean changed = previous == null || previous != active;
            boolean predictiveMovement = PhaseFlightSubmodule.INSTANCE.id().equals(submodule.id());
            if (dist == Dist.DEDICATED_SERVER
                    && player instanceof ServerPlayer serverPlayer
                    && ArmorPhaseFlightRules.shouldSyncClientActiveState(active, changed, predictiveMovement)) {
                PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new CelestweaveSubmoduleActivePacket(armorId, submodule.id(), active));
                if (active && (submodule.id().equals(FlightSubmodule.INSTANCE.id())
                        || submodule.id().equals("phase_flight"))) {
                    syncFlightInertiaToClient(serverPlayer, armor, armorId);
                }
            }
            if (!changed) {
                continue;
            }
            if (active) {
                submodule.onActivated(player, dist, armor);
            } else {
                submodule.onDeactivated(player, dist, armor);
            }
        }
    }

    public static void reconcileInstalledSubmodules(
            @Nullable Player player,
            ItemStack armor,
            HolderLookup.Provider registries,
            Dist dist) {
        ensureArmorId(armor);
        for (var submodule : collectSubmodules(armor, registries)) {
            submodule.onInstalled(player, dist, armor);
        }
    }

    public static void tickActiveSubmodules(
            @Nullable Player player,
            ItemStack armor,
            HolderLookup.Provider registries,
            Dist dist) {
        tickActiveSubmodules(player, armor, collectInstalledSubmoduleEntries(armor, registries), dist);
    }

    public static void tickActiveSubmodules(
            @Nullable Player player,
            ItemStack armor,
            List<InstalledSubmodule> installedSubmodules,
            Dist dist) {
        for (var entry : installedSubmodules) {
            var submodule = entry.submodule();
            if (!isSubmoduleRuntimeActive(armor, submodule.id())) {
                continue;
            }
            submodule.tickActive(player, dist, armor);
        }
    }

    public static Snapshot tickEquipped(Player player, ItemStack armor, HolderLookup.Provider registries) {
        return tickEquipped(player, armor, collectInstalledSubmoduleEntries(armor, registries), registries);
    }

    public static Snapshot tickEquipped(
            Player player,
            ItemStack armor,
            List<InstalledSubmodule> installedSubmodules,
            HolderLookup.Provider registries) {
        UUID id = ensureArmorId(armor);
        pruneRemovedRuntime(id, installedSubmoduleIds(installedSubmodules));
        return snapshot(player, armor, registries, true);
    }

    public static Snapshot snapshot(ItemStack armor, HolderLookup.Provider registries, boolean equipped) {
        return snapshot(null, armor, registries, equipped);
    }

    public static Snapshot snapshot(
            @Nullable Player player,
            ItemStack armor,
            HolderLookup.Provider registries,
            boolean equipped) {
        long stored = ArmorEnergyBuffer.read(armor, registries);
        long capacity = ArmorEnergyBuffer.capacity(armor, registries);
        boolean hasCore = hasCore(armor, registries);
        boolean hasEnergy = ArmorEnergyModuleStorage.capacityFe(armor, registries) > 0L;
        return new Snapshot(
                equipped,
                hasCore,
                hasEnergy,
                stored,
                capacity);
    }

    public static boolean isSubmodulePowered(ItemStack armor) {
        return true;
    }

    public static long readPersistedStoredEnergy(ItemStack armor) {
        return ArmorEnergyBuffer.read(armor);
    }

    public static long addStoredEnergy(ItemStack armor, HolderLookup.Provider registries, long amount) {
        return ArmorEnergyBuffer.receiveFe(armor, registries, (int) Math.min(Integer.MAX_VALUE, amount), false);
    }

    public static void flushRuntimeToNbt(ItemStack armor) {
    }

    public static void markClientActive(UUID armorId, String submoduleId, boolean active) {
        ArmorRuntimeRegistry.setClientSubmoduleActive(armorId, submoduleId, active);
    }

    public static boolean isClientSubmoduleActive(UUID armorId, String submoduleId) {
        return ArmorRuntimeRegistry.isClientSubmoduleActive(armorId, submoduleId);
    }

    public static boolean isAnyClientSubmoduleActive(String submoduleId) {
        return ArmorRuntimeRegistry.isAnyClientSubmoduleActive(submoduleId);
    }

    public static void clearClientActiveCache() {
        ArmorRuntimeRegistry.clearClientActiveCache();
        CLIENT_FLIGHT_INERTIA = true;
        CLIENT_FLIGHT_INERTIA_ARMOR_ID = null;
    }

    public static void forgetSubmoduleActiveCache(UUID armorId) {
        ArmorRuntimeRegistry.clear(armorId);
    }

    public static void clearTransientRuntime(ItemStack armor) {
        UUID armorId = getArmorId(armor);
        if (armorId == null) {
            return;
        }
    }

    public static void clearTransientRuntimeAndCaches(ItemStack armor) {
        UUID armorId = getArmorId(armor);
        if (armorId == null) {
            return;
        }
        forgetSubmoduleActiveCache(armorId);
    }

    private static String resolveSubmoduleId(ItemStack stack) {
        if (stack != null && !stack.isEmpty() && stack.getItem() instanceof ArmorEnergyModuleItem) {
            return ArmorEnergyModuleItem.MODULE_TYPE_ID;
        }
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof CelestweaveArmorSubmoduleItem provider)) {
            return "";
        }
        var ref = new String[]{""};
        provider.collectSubmodules(stack, submodule -> {
            if (submodule != null && !submodule.id().isBlank() && ref[0].isEmpty()) {
                ref[0] = submodule.id();
            }
        });
        return ref[0];
    }

    private static String resolveSubmoduleGroupId(ItemStack stack) {
        if (stack != null && !stack.isEmpty() && stack.getItem() instanceof ArmorEnergyModuleItem) {
            return ArmorEnergyModuleItem.MODULE_TYPE_ID;
        }
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof CelestweaveArmorSubmoduleItem provider)) {
            return "";
        }
        var ref = new String[]{""};
        provider.collectSubmodules(stack, submodule -> {
            if (submodule != null && !submodule.installGroupId().isBlank() && ref[0].isEmpty()) {
                ref[0] = submodule.installGroupId();
            }
        });
        return ref[0];
    }

    public static List<InstalledSubmodule> collectInstalledSubmoduleEntries(
            ItemStack armor,
            HolderLookup.Provider registries) {
        var result = new ArrayList<InstalledSubmodule>();
        for (var stack : loadModuleStacks(armor, registries)) {
            if (!(stack.getItem() instanceof CelestweaveArmorSubmoduleItem provider)) {
                continue;
            }
            int count = Math.max(1, stack.getCount());
            ItemStack installedStack = stack.copyWithCount(count);
            provider.collectSubmodules(installedStack, submodule -> {
                if (submodule != null && !submodule.id().isBlank()) {
                    result.add(new InstalledSubmodule(installedStack, submodule, count));
                }
            });
        }
        return List.copyOf(result);
    }

    private static void pruneRemovedRuntime(UUID armorId, java.util.Set<String> installedIds) {
        for (String submoduleId : List.copyOf(ArmorRuntimeRegistry.submoduleIds(armorId))) {
            if (installedIds.contains(submoduleId)) {
                continue;
            }
            ArmorRuntimeRegistry.removeSubmodule(armorId, submoduleId);
        }
    }

    private static Set<String> installedSubmoduleIds(ItemStack armor, HolderLookup.Provider registries) {
        return installedSubmoduleIds(collectInstalledSubmoduleEntries(armor, registries));
    }

    private static Set<String> installedSubmoduleIds(List<InstalledSubmodule> installedSubmodules) {
        var installedIds = new HashSet<String>();
        for (var entry : installedSubmodules) {
            installedIds.add(entry.submodule().id());
        }
        return installedIds;
    }

    private static void setSubmoduleRuntimeActive(ItemStack armor, String submoduleId, boolean active) {
        UUID id = ensureArmorId(armor);
        ArmorRuntimeRegistry.setSubmoduleRuntimeActive(id, submoduleId, active);
    }

    private static CompoundTag getStoredSubmoduleData(ItemStack armor, String submoduleId) {
        return ArmorPersistentData.getSubmoduleData(armor, submoduleId);
    }

    private static void setStoredSubmoduleData(ItemStack armor, String submoduleId, CompoundTag data) {
        ArmorPersistentData.setSubmoduleData(armor, submoduleId, data);
    }

    public static void setClientFlightInertia(UUID armorId, boolean inertiaEnabled) {
        CLIENT_FLIGHT_INERTIA = inertiaEnabled;
        CLIENT_FLIGHT_INERTIA_ARMOR_ID = armorId;
    }

    public static boolean getClientFlightInertia() {
        return CLIENT_FLIGHT_INERTIA;
    }

    private static void syncFlightInertiaToClient(ServerPlayer player, ItemStack armor, UUID armorId) {
        boolean phaseFlightActive = isSubmoduleRuntimeActive(armor, PhaseFlightSubmodule.INSTANCE.id());
        boolean inertia = phaseFlightActive
                ? PhaseFlightSubmodule.isInertiaEnabled(armor)
                : FlightSubmodule.isInertiaEnabled(armor);
        PacketDistributor.sendToPlayer(player, new FlightInertiaSyncPacket(armorId, inertia));
    }

    public static void syncFlightInertiaToClientIfFlight(ServerPlayer player, ItemStack armor) {
        UUID armorId = getArmorId(armor);
        if (armorId == null) return;
        boolean flightActive = isSubmoduleRuntimeActive(armor, FlightSubmodule.INSTANCE.id())
                || isSubmoduleRuntimeActive(armor, "phase_flight");
        if (flightActive) {
            syncFlightInertiaToClient(player, armor, armorId);
        }
    }

    public record InstalledSubmodule(ItemStack stack, CelestweaveArmorSubmodule submodule, int count) {
    }

    public record Snapshot(
            boolean equipped,
            boolean hasCore,
            boolean hasEnergyModule,
            long storedEnergy,
            long energyCapacity
    ) {}
}
