package com.moakiee.ae2lt.overload.armor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;

import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.device.overload.OverloadRuntime;
import com.moakiee.ae2lt.overload.armor.module.OverloadArmorSubmodule;
import com.moakiee.ae2lt.overload.armor.module.OverloadArmorSubmoduleItem;
import com.moakiee.ae2lt.registry.ModDataComponents;
import com.moakiee.ae2lt.registry.ModItems;

public final class OverloadArmorState {
    public static final int SLOT_CORE = 0;
    public static final int SLOT_ENERGY = 1;
    public static final int SLOT_COUNT = 2;
    public static final int LOCK_TRIGGER_TICKS = 60;
    public static final int LOCK_DURATION_TICKS = 600;

    private static final int MAX_MODULE_TYPES = 32;
    private static final String TAG_ROOT = "OverloadArmor";
    private static final String TAG_ARMOR_ID = "ArmorId";
    private static final String TAG_INSTALLED_SUBMODULES = "InstalledSubmodules";
    private static final String TAG_SUBMODULE_DATA = "SubmoduleData";
    private static final String TAG_SUBMODULE_RUNTIME = "SubmoduleRuntime";
    private static final String TAG_FEATURE_TOGGLES = "FeatureToggles";
    private static final String TAG_RUNTIME_ACTIVE = "Active";
    private static final String TAG_RUNTIME_DYNAMIC_LOAD = "DynamicLoad";

    private static final java.util.Map<String, Boolean> SERVER_ACTIVE_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, Boolean> CLIENT_ACTIVE_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, SubmoduleRuntime> SUBMODULE_RUNTIME_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    private OverloadArmorState() {
    }

    public static UUID ensureArmorId(ItemStack armor) {
        UUID existing = getArmorId(armor);
        if (existing != null) {
            return existing;
        }
        UUID created = UUID.randomUUID();
        CustomData.update(DataComponents.CUSTOM_DATA, armor, root -> {
            var armorTag = armorTag(root);
            armorTag.putUUID(TAG_ARMOR_ID, created);
            root.put(TAG_ROOT, armorTag);
        });
        return created;
    }

    @Nullable
    public static UUID getArmorId(ItemStack armor) {
        if (armor == null || armor.isEmpty()) {
            return null;
        }
        var root = rootTag(armor);
        if (!root.contains(TAG_ROOT, CompoundTag.TAG_COMPOUND)) {
            return null;
        }
        var armorTag = root.getCompound(TAG_ROOT);
        return armorTag.hasUUID(TAG_ARMOR_ID) ? armorTag.getUUID(TAG_ARMOR_ID) : null;
    }

    public static ItemStack getSlot(ItemStack armor, HolderLookup.Provider registries, int slot) {
        if (slot == SLOT_CORE) {
            return armor.getOrDefault(ModDataComponents.ARMOR_STRUCTURAL_CORE.get(), ItemStack.EMPTY).copyWithCount(1);
        }
        if (slot == SLOT_ENERGY) {
            return armor.getOrDefault(ModDataComponents.ARMOR_STRUCTURAL_ENERGY_MODULE.get(), ItemStack.EMPTY)
                    .copyWithCount(1);
        }
        return ItemStack.EMPTY;
    }

    public static void setSlot(ItemStack armor, HolderLookup.Provider registries, int slot, ItemStack stack) {
        if (slot == SLOT_CORE) {
            setComponentSlot(armor, ModDataComponents.ARMOR_STRUCTURAL_CORE.get(), stack);
        } else if (slot == SLOT_ENERGY) {
            setComponentSlot(armor, ModDataComponents.ARMOR_STRUCTURAL_ENERGY_MODULE.get(), stack);
            ArmorEnergyBuffer.clamp(armor);
        }
    }

    private static void setComponentSlot(
            ItemStack armor,
            net.minecraft.core.component.DataComponentType<ItemStack> component,
            ItemStack stack) {
        if (armor == null || armor.isEmpty()) {
            return;
        }
        if (stack == null || stack.isEmpty()) {
            armor.remove(component);
        } else {
            armor.set(component, stack.copyWithCount(1));
        }
    }

    public static boolean canInstallCore(ItemStack armor, HolderLookup.Provider registries, ItemStack core) {
        return core != null && !core.isEmpty() && core.is(ModItems.ULTIMATE_OVERLOAD_CORE.get());
    }

    public static int getBaseOverload(ItemStack armor, HolderLookup.Provider registries) {
        return hasCore(armor, registries) ? armorPart(armor).dynamicCap() : 0;
    }

    public static int getBaseOverloadFor(ItemStack core) {
        return core != null && core.is(ModItems.ULTIMATE_OVERLOAD_CORE.get()) ? 128 : 0;
    }

    public static boolean hasCore(ItemStack armor, HolderLookup.Provider registries) {
        return !getSlot(armor, registries, SLOT_CORE).isEmpty();
    }

    public static ArmorPart armorPart(ItemStack armor) {
        if (armor != null && armor.getItem() instanceof BaseOverloadArmorItem item) {
            return item.armorPart();
        }
        return ArmorPart.CHEST;
    }

    public static boolean canInstallModule(ItemStack armor, HolderLookup.Provider registries, ItemStack candidate) {
        if (candidate == null || candidate.isEmpty()) {
            return false;
        }
        if (!(candidate.getItem() instanceof OverloadArmorSubmoduleItem provider)) {
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
            return stack.copy();
        }
        return ItemStack.EMPTY;
    }

    public static List<ItemStack> loadModuleStacks(ItemStack armor, HolderLookup.Provider registries) {
        var root = rootTag(armor);
        if (!root.contains(TAG_ROOT, CompoundTag.TAG_COMPOUND)) {
            return List.of();
        }
        var armorTag = root.getCompound(TAG_ROOT);
        if (!armorTag.contains(TAG_INSTALLED_SUBMODULES, CompoundTag.TAG_LIST)) {
            return List.of();
        }
        var list = armorTag.getList(TAG_INSTALLED_SUBMODULES, CompoundTag.TAG_COMPOUND);
        var result = new ArrayList<ItemStack>(list.size());
        for (int i = 0; i < list.size(); i++) {
            ItemStack stack = ItemStack.parseOptional(registries, list.getCompound(i).copy());
            if (!stack.isEmpty()) {
                result.add(stack);
            }
        }
        return List.copyOf(result);
    }

    private static void saveModuleStacks(ItemStack armor, HolderLookup.Provider registries, List<ItemStack> stacks) {
        CustomData.update(DataComponents.CUSTOM_DATA, armor, root -> {
            var armorTag = armorTag(root);
            var out = new ListTag();
            var merged = new LinkedHashMap<String, ItemStack>();
            for (var stack : stacks) {
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                String id = resolveSubmoduleId(stack);
                if (id.isBlank()) {
                    continue;
                }
                merged.compute(id, (ignored, existing) -> {
                    if (existing == null) {
                        return stack.copy();
                    }
                    existing.grow(stack.getCount());
                    return existing;
                });
            }
            int writtenTypes = 0;
            int writtenUnits = 0;
            int maxUnits = armorPart(armor).moduleSlotCount();
            for (var stack : merged.values()) {
                if (writtenTypes >= MAX_MODULE_TYPES || writtenUnits >= maxUnits) {
                    break;
                }
                int count = Math.min(Math.max(1, stack.getCount()), maxUnits - writtenUnits);
                out.add(stack.copyWithCount(count).saveOptional(registries));
                writtenTypes++;
                writtenUnits += count;
            }
            if (out.isEmpty()) {
                armorTag.remove(TAG_INSTALLED_SUBMODULES);
            } else {
                armorTag.put(TAG_INSTALLED_SUBMODULES, out);
            }
            root.put(TAG_ROOT, armorTag);
        });
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

    private static int getInstalledUnitCount(ItemStack armor, HolderLookup.Provider registries) {
        int total = 0;
        for (var stack : loadModuleStacks(armor, registries)) {
            total += Math.max(1, stack.getCount());
        }
        return total;
    }

    public static int computeTotalIdleOverload(ItemStack armor, HolderLookup.Provider registries) {
        return 0;
    }

    public static List<OverloadArmorSubmodule> collectSubmodules(ItemStack armor, HolderLookup.Provider registries) {
        return collectInstalledSubmoduleEntries(armor, registries).stream()
                .map(InstalledSubmodule::submodule)
                .toList();
    }

    public static String moduleTypeId(ItemStack stack) {
        return resolveSubmoduleId(stack);
    }

    public static int getSubmoduleMaxInstallAmountForStack(ItemStack stack) {
        int max = 0;
        if (stack != null && stack.getItem() instanceof OverloadArmorSubmoduleItem provider) {
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

    public static int getSubmoduleMaxInstallAmount(OverloadArmorSubmodule submodule) {
        return submodule == null ? 0 : Math.max(0, submodule.getMaxInstallAmount());
    }

    public static boolean isSubmoduleInstalled(ItemStack armor, String submoduleId) {
        return getInstalledAmount(armor, null, submoduleId) > 0;
    }

    public static boolean isSubmoduleEnabled(ItemStack armor, OverloadArmorSubmodule submodule) {
        return submodule != null && isSubmoduleEnabled(armor, submodule.id(), submodule.defaultEnabled());
    }

    public static boolean isSubmoduleEnabled(ItemStack armor, String submoduleId, boolean defaultEnabled) {
        var tag = armorTag(rootTag(armor));
        if (!tag.contains(TAG_FEATURE_TOGGLES, CompoundTag.TAG_COMPOUND)) {
            return defaultEnabled;
        }
        var toggles = tag.getCompound(TAG_FEATURE_TOGGLES);
        return toggles.contains(submoduleId, Tag.TAG_BYTE) ? toggles.getBoolean(submoduleId) : defaultEnabled;
    }

    public static void setSubmoduleEnabled(ItemStack armor, OverloadArmorSubmodule submodule, boolean enabled) {
        if (submodule != null) {
            setSubmoduleEnabled(armor, submodule.id(), enabled, submodule.defaultEnabled());
        }
    }

    public static void setSubmoduleEnabled(ItemStack armor, String submoduleId, boolean enabled, boolean defaultEnabled) {
        if (submoduleId == null || submoduleId.isBlank()) {
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, armor, root -> {
            var tag = armorTag(root);
            var toggles = tag.contains(TAG_FEATURE_TOGGLES, CompoundTag.TAG_COMPOUND)
                    ? tag.getCompound(TAG_FEATURE_TOGGLES)
                    : new CompoundTag();
            if (enabled == defaultEnabled) {
                toggles.remove(submoduleId);
            } else {
                toggles.putBoolean(submoduleId, enabled);
            }
            if (toggles.isEmpty()) {
                tag.remove(TAG_FEATURE_TOGGLES);
            } else {
                tag.put(TAG_FEATURE_TOGGLES, toggles);
            }
            root.put(TAG_ROOT, tag);
        });
    }

    public static int buildSubmoduleMask(ItemStack armor, List<OverloadArmorSubmodule> submodules) {
        int mask = 0;
        int limit = Math.min(submodules.size(), Integer.SIZE - 1);
        for (int i = 0; i < limit; i++) {
            if (isSubmoduleEnabled(armor, submodules.get(i))) {
                mask |= 1 << i;
            }
        }
        return mask;
    }

    public static CompoundTag getSubmoduleData(ItemStack armor, OverloadArmorSubmodule submodule) {
        return submodule == null ? new CompoundTag() : getStoredSubmoduleData(armor, submodule.id());
    }

    public static void setSubmoduleData(ItemStack armor, OverloadArmorSubmodule submodule, CompoundTag data) {
        if (submodule != null) {
            setStoredSubmoduleData(armor, submodule.id(), data);
        }
    }

    public static int getSubmoduleDynamicLoad(ItemStack armor, OverloadArmorSubmodule submodule) {
        return submodule == null ? 0 : getSubmoduleDynamicLoadFor(armor, submodule.id());
    }

    public static int getSubmoduleDynamicLoadFor(ItemStack armor, String submoduleId) {
        return getSubmoduleRuntime(armor, submoduleId).dynamicLoad();
    }

    public static boolean isSubmoduleRuntimeActive(ItemStack armor, String submoduleId) {
        return getSubmoduleRuntime(armor, submoduleId).active();
    }

    public static boolean isSubmoduleActive(ItemStack armor, OverloadArmorSubmodule submodule, HolderLookup.Provider registries, boolean equipped) {
        return submodule != null && isSubmoduleRuntimeActive(armor, submodule.id());
    }

    public static void syncSubmoduleActiveState(
            @Nullable Player player,
            ItemStack armor,
            HolderLookup.Provider registries,
            boolean equipped,
            Dist dist) {
        UUID armorId = ensureArmorId(armor);
        boolean powered = !snapshot(player, armor, registries, equipped).locked();
        for (var submodule : collectSubmodules(armor, registries)) {
            boolean active = equipped
                    && hasCore(armor, registries)
                    && powered
                    && isSubmoduleEnabled(armor, submodule);
            String key = cacheKey(armorId, submodule.id());
            var cache = dist == Dist.CLIENT ? CLIENT_ACTIVE_CACHE : SERVER_ACTIVE_CACHE;
            Boolean previous = cache.put(key, active);
            setSubmoduleRuntimeActive(armor, submodule.id(), active);
            if (previous != null && previous == active) {
                continue;
            }
            if (active) {
                submodule.onActivated(player, dist, armor);
            } else {
                armorRuntime(armorId).bucket().clear(submodule.id());
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
        for (var entry : collectInstalledSubmoduleEntries(armor, registries)) {
            var submodule = entry.submodule();
            if (!isSubmoduleRuntimeActive(armor, submodule.id())) {
                continue;
            }
            int load = Math.max(0, submodule.tickActive(player, dist, armor)) * entry.count();
            setSubmoduleRuntimeDynamicLoad(armor, submodule.id(), load);
        }
    }

    public static Snapshot tickEquipped(Player player, ItemStack armor, HolderLookup.Provider registries) {
        UUID id = ensureArmorId(armor);
        var runtime = armorRuntime(id);
        var entries = collectInstalledSubmoduleEntries(armor, registries);
        var installedIds = new HashSet<String>();
        for (var entry : entries) {
            installedIds.add(entry.submodule().id());
        }
        pruneRemovedRuntime(id, installedIds, runtime);
        for (var entry : entries) {
            var submodule = entry.submodule();
            int load = isSubmoduleRuntimeActive(armor, submodule.id())
                    ? getSubmoduleDynamicLoadFor(armor, submodule.id())
                    : 0;
            if (load > 0) {
                runtime.bucket().setState(submodule.id(), load);
            } else {
                runtime.bucket().clearState(submodule.id());
            }
        }
        runtime.tick(getBaseOverload(armor, registries));
        for (var entry : entries) {
            var submodule = entry.submodule();
            int load = isSubmoduleRuntimeActive(armor, submodule.id())
                    ? runtime.bucket().currentFor(submodule.id())
                    : 0;
            setSubmoduleRuntimeDynamicLoad(armor, submodule.id(), load);
        }
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
        UUID id = getArmorId(armor);
        var runtime = id == null ? null : armorRuntime(id);
        int currentLoad = runtime == null ? 0 : runtime.currentLoad();
        int lockedTicks = runtime == null ? 0 : runtime.dynamics().lockTicksRemaining();
        int debtTicks = runtime == null ? 0 : runtime.dynamics().debtTicks();
        long stored = ArmorEnergyBuffer.read(armor);
        long capacity = ArmorEnergyBuffer.capacity(armor);
        boolean hasCore = hasCore(armor, registries);
        boolean hasEnergy = !getSlot(armor, registries, SLOT_ENERGY).isEmpty();
        int cap = getBaseOverload(armor, registries);
        int moduleLoad = 0;
        return new Snapshot(
                equipped,
                hasCore,
                hasEnergy,
                stored,
                debtTicks,
                lockedTicks,
                cap,
                moduleLoad,
                currentLoad,
                capacity);
    }

    public static boolean isSubmodulePowered(ItemStack armor) {
        return snapshot(armor, null, true).lockedTicks() <= 0;
    }

    public static long readPersistedStoredEnergy(ItemStack armor) {
        return ArmorEnergyBuffer.read(armor);
    }

    public static long addStoredEnergy(ItemStack armor, HolderLookup.Provider registries, long amount) {
        return ArmorEnergyBuffer.receiveFe(armor, (int) Math.min(Integer.MAX_VALUE, amount), false);
    }

    public static void addPulseLoad(ItemStack armor, int load) {
        addPulseLoad(armor, "", load);
    }

    public static void addPulseLoad(ItemStack armor, String submoduleId, int load) {
        if (armor == null || armor.isEmpty() || load <= 0) {
            return;
        }
        armorRuntime(ensureArmorId(armor)).bucket().addPulse(submoduleId, load);
    }

    public static void flushRuntimeToNbt(ItemStack armor) {
    }

    public static void markClientActive(UUID armorId, String submoduleId, boolean active) {
        if (armorId != null && submoduleId != null && !submoduleId.isBlank()) {
            CLIENT_ACTIVE_CACHE.put(cacheKey(armorId, submoduleId), active);
        }
    }

    public static boolean isClientSubmoduleActive(UUID armorId, String submoduleId) {
        if (armorId == null || submoduleId == null || submoduleId.isBlank()) {
            return false;
        }
        return CLIENT_ACTIVE_CACHE.getOrDefault(cacheKey(armorId, submoduleId), false);
    }

    public static void forgetSubmoduleActiveCache(UUID armorId) {
        if (armorId == null) {
            return;
        }
        String prefix = armorId + "#";
        SERVER_ACTIVE_CACHE.keySet().removeIf(key -> key.startsWith(prefix));
        CLIENT_ACTIVE_CACHE.keySet().removeIf(key -> key.startsWith(prefix));
        SUBMODULE_RUNTIME_CACHE.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private static String resolveSubmoduleId(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof OverloadArmorSubmoduleItem provider)) {
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

    private static List<InstalledSubmodule> collectInstalledSubmoduleEntries(
            ItemStack armor,
            HolderLookup.Provider registries) {
        var result = new ArrayList<InstalledSubmodule>();
        for (var stack : loadModuleStacks(armor, registries)) {
            if (!(stack.getItem() instanceof OverloadArmorSubmoduleItem provider)) {
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

    private static int idleLoadFor(
            @Nullable Player player,
            ItemStack armor,
            InstalledSubmodule entry,
            Dist dist) {
        return Math.max(0, entry.submodule().getIdleOverloaded(player, dist, armor)) * entry.count();
    }

    private static int computeStackIdleOverload(
            ItemStack armor,
            ItemStack stack,
            int count,
            Dist dist) {
        return 0;
    }

    private static void pruneRemovedRuntime(UUID armorId, java.util.Set<String> installedIds, OverloadRuntime runtime) {
        String prefix = armorId + "#";
        for (String key : List.copyOf(SUBMODULE_RUNTIME_CACHE.keySet())) {
            if (!key.startsWith(prefix)) {
                continue;
            }
            String submoduleId = key.substring(prefix.length());
            if (installedIds.contains(submoduleId)) {
                continue;
            }
            runtime.bucket().clear(submoduleId);
            SUBMODULE_RUNTIME_CACHE.remove(key);
            SERVER_ACTIVE_CACHE.remove(key);
            CLIENT_ACTIVE_CACHE.remove(key);
        }
    }

    private static OverloadRuntime armorRuntime(UUID armorId) {
        return OverloadRuntime.get(
                armorId,
                AE2LTCommonConfig.overloadArmorPulseDecay(),
                AE2LTCommonConfig.overloadArmorPulseMaxTicks(),
                AE2LTCommonConfig.overloadArmorPulseEpsilon(),
                AE2LTCommonConfig.overloadArmorLockTriggerTicks(),
                AE2LTCommonConfig.overloadArmorLockDurationTicks());
    }

    private static void setSubmoduleRuntimeActive(ItemStack armor, String submoduleId, boolean active) {
        UUID id = ensureArmorId(armor);
        String key = cacheKey(id, submoduleId);
        var previous = SUBMODULE_RUNTIME_CACHE.get(key);
        int load = active && previous != null ? previous.dynamicLoad() : 0;
        SUBMODULE_RUNTIME_CACHE.put(key, new SubmoduleRuntime(active, load));
    }

    private static void setSubmoduleRuntimeDynamicLoad(ItemStack armor, String submoduleId, int dynamicLoad) {
        UUID id = ensureArmorId(armor);
        String key = cacheKey(id, submoduleId);
        var previous = SUBMODULE_RUNTIME_CACHE.get(key);
        boolean active = previous != null && previous.active();
        SUBMODULE_RUNTIME_CACHE.put(key, new SubmoduleRuntime(active, Math.max(0, dynamicLoad)));
    }

    private static SubmoduleRuntime getSubmoduleRuntime(ItemStack armor, String submoduleId) {
        UUID id = getArmorId(armor);
        if (id != null) {
            var cached = SUBMODULE_RUNTIME_CACHE.get(cacheKey(id, submoduleId));
            if (cached != null) {
                return cached;
            }
        }
        return new SubmoduleRuntime(false, 0);
    }

    private static CompoundTag getStoredSubmoduleData(ItemStack armor, String submoduleId) {
        var root = rootTag(armor);
        if (!root.contains(TAG_ROOT, CompoundTag.TAG_COMPOUND)) {
            return new CompoundTag();
        }
        var armorTag = root.getCompound(TAG_ROOT);
        if (!armorTag.contains(TAG_SUBMODULE_DATA, CompoundTag.TAG_COMPOUND)) {
            return new CompoundTag();
        }
        var data = armorTag.getCompound(TAG_SUBMODULE_DATA);
        return data.contains(submoduleId, CompoundTag.TAG_COMPOUND)
                ? data.getCompound(submoduleId).copy()
                : new CompoundTag();
    }

    private static void setStoredSubmoduleData(ItemStack armor, String submoduleId, CompoundTag data) {
        CustomData.update(DataComponents.CUSTOM_DATA, armor, root -> {
            var armorTag = armorTag(root);
            var allData = armorTag.contains(TAG_SUBMODULE_DATA, CompoundTag.TAG_COMPOUND)
                    ? armorTag.getCompound(TAG_SUBMODULE_DATA)
                    : new CompoundTag();
            if (data == null || data.isEmpty()) {
                allData.remove(submoduleId);
            } else {
                allData.put(submoduleId, data.copy());
            }
            if (allData.isEmpty()) {
                armorTag.remove(TAG_SUBMODULE_DATA);
            } else {
                armorTag.put(TAG_SUBMODULE_DATA, allData);
            }
            root.put(TAG_ROOT, armorTag);
        });
    }

    private static CompoundTag rootTag(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return new CompoundTag();
        }
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private static CompoundTag armorTag(CompoundTag root) {
        return root.contains(TAG_ROOT, CompoundTag.TAG_COMPOUND)
                ? root.getCompound(TAG_ROOT)
                : new CompoundTag();
    }

    private static String cacheKey(UUID armorId, String submoduleId) {
        return armorId + "#" + submoduleId;
    }

    private record SubmoduleRuntime(boolean active, int dynamicLoad) {
    }

    private record InstalledSubmodule(ItemStack stack, OverloadArmorSubmodule submodule, int count) {
    }

    public record Snapshot(
            boolean equipped,
            boolean hasCore,
            boolean hasEnergyModule,
            long storedEnergy,
            int debtTicks,
            int lockedTicks,
            int baseOverload,
            int moduleLoad,
            int currentLoad,
            long energyCapacity
    ) {
        public int remainingLoad() {
            return baseOverload - currentLoad;
        }

        public boolean overloaded() {
            return currentLoad > baseOverload;
        }

        public boolean locked() {
            return lockedTicks > 0;
        }
    }
}
