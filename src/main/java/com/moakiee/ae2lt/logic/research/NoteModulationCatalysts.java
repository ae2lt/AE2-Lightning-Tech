package com.moakiee.ae2lt.logic.research;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.moakiee.ae2lt.registry.ModItems;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.RegistryObject;

/**
 * 铁砧调制研究笔记时,允许使用的催化物(每种 {@link RitualGoal} 对应 1 种物品)。
 * 注意:不向 JEI 注册任何 recipe,映射仅由 AnvilUpdateEvent 在运行时识别。
 */
public final class NoteModulationCatalysts {

    private NoteModulationCatalysts() {
    }

    private static final Map<RitualGoal, CatalystEntry> MAPPING = buildMapping();

    private static Map<RitualGoal, CatalystEntry> buildMapping() {
        Map<RitualGoal, CatalystEntry> map = new EnumMap<>(RitualGoal.class);
        map.put(RitualGoal.HIGH_VOLTAGE, new CatalystEntry(ModItems.LIGHTNING_CELL_COMPONENT_III));
        map.put(RitualGoal.EXTREME_HIGH_VOLTAGE, new CatalystEntry(ModItems.LIGHTNING_CELL_COMPONENT_V));
        map.put(RitualGoal.LIGHTNING_COLLAPSE_MATRIX, new CatalystEntry(ModItems.LIGHTNING_COLLAPSE_MATRIX));
        map.put(RitualGoal.INFINITE_STORAGE_CELL, new CatalystEntry(ModItems.ULTIMATE_OVERLOAD_CORE));
        return Map.copyOf(map);
    }

    public static @Nullable Item getCatalyst(RitualGoal goal) {
        CatalystEntry entry = MAPPING.get(goal);
        return entry != null ? entry.item() : null;
    }

    public static Optional<RitualGoal> findGoal(ItemStack catalystStack) {
        if (catalystStack.isEmpty()) {
            return Optional.empty();
        }
        for (Map.Entry<RitualGoal, CatalystEntry> e : MAPPING.entrySet()) {
            Item candidate = e.getValue().item();
            if (candidate == null) {
                continue;
            }
            if (catalystStack.is(candidate)) {
                return Optional.of(e.getKey());
            }
        }
        return Optional.empty();
    }

    private record CatalystEntry(RegistryObject<? extends Item> holder) {
        @Nullable
        Item item() {
            return holder != null && holder.isPresent() ? holder.get() : null;
        }
    }
}

