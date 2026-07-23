package com.moakiee.ae2lt.celestweave.state;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * Immutable per-stack persistent state for one Celestweave armor piece. Backs a
 * data component, so it is auto-synced to clients and saved with the stack.
 * Mutations return a new instance (Mekanism-style), which keeps callers from
 * aliasing the stored map and forces a {@code stack.set} to re-sync.
 *
 * <p>{@code energyModuleCapacityFe} empty means "never computed" (legacy stacks);
 * present (even 0) means the cache is valid.
 */
public record CelestweaveModuleContainer(
        Optional<UUID> armorId,
        List<ItemStack> modules,
        Map<String, Boolean> toggles,
        Map<String, CompoundTag> submoduleData,
        Optional<Long> energyModuleCapacityFe) {

    public static final CelestweaveModuleContainer EMPTY = new CelestweaveModuleContainer(
            Optional.empty(), List.of(), Map.of(), Map.of(), Optional.empty());

    public static final Codec<CelestweaveModuleContainer> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.optionalFieldOf("armor_id").forGetter(CelestweaveModuleContainer::armorId),
            ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("modules", List.of())
                    .forGetter(CelestweaveModuleContainer::modules),
            Codec.unboundedMap(Codec.STRING, Codec.BOOL).optionalFieldOf("toggles", Map.of())
                    .forGetter(CelestweaveModuleContainer::toggles),
            Codec.unboundedMap(Codec.STRING, CompoundTag.CODEC).optionalFieldOf("submodule_data", Map.of())
                    .forGetter(CelestweaveModuleContainer::submoduleData),
            Codec.LONG.optionalFieldOf("energy_capacity_fe").forGetter(CelestweaveModuleContainer::energyModuleCapacityFe)
    ).apply(instance, CelestweaveModuleContainer::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CelestweaveModuleContainer> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), CelestweaveModuleContainer::armorId,
                    ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()), CelestweaveModuleContainer::modules,
                    ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.BOOL),
                    CelestweaveModuleContainer::toggles,
                    ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.COMPOUND_TAG),
                    CelestweaveModuleContainer::submoduleData,
                    ByteBufCodecs.optional(ByteBufCodecs.VAR_LONG), CelestweaveModuleContainer::energyModuleCapacityFe,
                    CelestweaveModuleContainer::new);

    public CelestweaveModuleContainer {
        modules = copyModules(modules);
        toggles = toggles == null ? Map.of() : Map.copyOf(toggles);
        submoduleData = copySubmoduleData(submoduleData);
    }

    @Override
    public List<ItemStack> modules() {
        return copyModules(modules);
    }

    @Override
    public Map<String, CompoundTag> submoduleData() {
        return copySubmoduleData(submoduleData);
    }

    public CelestweaveModuleContainer withArmorId(UUID id) {
        return new CelestweaveModuleContainer(Optional.ofNullable(id), modules, toggles, submoduleData, energyModuleCapacityFe);
    }

    public CelestweaveModuleContainer withModules(List<ItemStack> newModules, Optional<Long> capacityFe) {
        return new CelestweaveModuleContainer(armorId, newModules, toggles, submoduleData, capacityFe);
    }

    public CelestweaveModuleContainer withToggles(Map<String, Boolean> newToggles) {
        return new CelestweaveModuleContainer(armorId, modules, newToggles, submoduleData, energyModuleCapacityFe);
    }

    public CelestweaveModuleContainer withSubmoduleData(Map<String, CompoundTag> newData) {
        return new CelestweaveModuleContainer(armorId, modules, toggles, newData, energyModuleCapacityFe);
    }

    public CelestweaveModuleContainer withCapacity(Optional<Long> capacityFe) {
        return new CelestweaveModuleContainer(armorId, modules, toggles, submoduleData, capacityFe);
    }

    // Custom equals/hashCode — see javadoc below on why the record default is insufficient.

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CelestweaveModuleContainer other)) {
            return false;
        }
        return Objects.equals(armorId, other.armorId)
                && modulesEquals(modules, other.modules)
                && Objects.equals(toggles, other.toggles)
                && submoduleDataEquals(submoduleData, other.submoduleData)
                && Objects.equals(energyModuleCapacityFe, other.energyModuleCapacityFe);
    }

    @Override
    public int hashCode() {
        return Objects.hash(armorId, modulesHash(modules), toggles, submoduleDataHash(submoduleData), energyModuleCapacityFe);
    }

    /**
     * Compare two module lists by content. The record's auto-generated equals
     * delegates to {@code List.equals} which calls {@code ItemStack.equals}.
     * {@code ItemStack} does NOT override {@code equals} — two identical-looking
     * copies returned by the network {@code STREAM_CODEC} are different objects
     * and therefore never equal. This caused {@code isSameItemSameComponents} to
     * always return {@code false} inside creative-mode inventory slot handling,
     * which triggered repeated {@code ARMOR_EQUIP_GENERIC} (issue #20).
     */
    private static boolean modulesEquals(List<ItemStack> a, List<ItemStack> b) {
        if (a == b) {
            return true;
        }
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!ItemStack.matches(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static int modulesHash(List<ItemStack> modules) {
        int hash = 0;
        for (ItemStack stack : modules) {
            hash = 31 * hash + ItemStack.hashItemAndComponents(stack);
        }
        return hash;
    }

    /**
     * Compare two submodule-data maps by content. {@code CompoundTag.equals}
     * delegates to {@code Map.equals} which calls {@code Tag.equals} on each
     * nested value. {@code Tag} does NOT override {@code equals}, so two
     * CompoundTags with identical content but created via {@code copy()} are
     * never equal. Use NBT string comparison to work around this (issue #20,
     * "with core" variant — submodules are active only when the core is
     * installed).
     */
    private static boolean submoduleDataEquals(Map<String, CompoundTag> a, Map<String, CompoundTag> b) {
        if (a == b) {
            return true;
        }
        if (a.size() != b.size()) {
            return false;
        }
        for (var entry : a.entrySet()) {
            CompoundTag bValue = b.get(entry.getKey());
            if (bValue == null) {
                return false;
            }
            if (!entry.getValue().toString().equals(bValue.toString())) {
                return false;
            }
        }
        return true;
    }

    private static int submoduleDataHash(Map<String, CompoundTag> data) {
        int hash = 0;
        for (var entry : data.entrySet()) {
            hash = 31 * hash + entry.getKey().hashCode();
            hash = 31 * hash + entry.getValue().toString().hashCode();
        }
        return hash;
    }

    private static List<ItemStack> copyModules(List<ItemStack> modules) {
        if (modules == null || modules.isEmpty()) {
            return List.of();
        }
        return modules.stream()
                .filter(stack -> stack != null && !stack.isEmpty())
                .toList();
    }

    private static Map<String, CompoundTag> copySubmoduleData(Map<String, CompoundTag> data) {
        if (data == null || data.isEmpty()) {
            return Map.of();
        }
        Map<String, CompoundTag> copy = new LinkedHashMap<>();
        for (var entry : data.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                continue;
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        return Map.copyOf(copy);
    }
}