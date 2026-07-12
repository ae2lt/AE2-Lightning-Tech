package com.moakiee.ae2lt.logic.tianshu.maintenance;

import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

/** Enumerates every stored or craftable exact variant in one ignore-secondary item group. */
public final class MaintenanceVariantService {
    public static List<Variant> list(MEStorage storage, ICraftingService crafting, AEKey selected) {
        if (storage == null || crafting == null || selected == null) return List.of();
        return list(storage.getAvailableStacks(),
                crafting.getCraftables(key -> sameGroup(selected, key)), selected);
    }

    public static List<Variant> list(
            KeyCounter stored, Iterable<AEKey> craftables, AEKey selected) {
        if (selected == null) return List.of();
        var variants = new LinkedHashMap<AEKey, MutableVariant>();
        variants.put(selected, new MutableVariant());
        if (stored != null) {
            for (var entry : stored) {
                if (!sameGroup(selected, entry.getKey())) continue;
                variants.computeIfAbsent(entry.getKey(), ignored -> new MutableVariant()).stored =
                        Math.max(0L, entry.getLongValue());
            }
        }
        if (craftables != null) {
            for (var key : craftables) {
                if (key != null && sameGroup(selected, key)) {
                    variants.computeIfAbsent(key, ignored -> new MutableVariant()).craftable = true;
                }
            }
        }
        var result = new ArrayList<Variant>(variants.size());
        variants.forEach((key, value) -> result.add(new Variant(key, value.stored, value.craftable)));
        result.sort(Comparator.comparing(entry -> stableKey(entry.key())));
        return List.copyOf(result);
    }

    private static boolean sameGroup(AEKey left, AEKey right) {
        return left != null && right != null && left.dropSecondary().equals(right.dropSecondary());
    }

    private static String stableKey(AEKey key) {
        return key.getId() + "|" + String.valueOf(key.getPrimaryKey()) + "|" + key.hashCode();
    }

    public record Variant(AEKey key, long storedAmount, boolean craftable) { }
    private static final class MutableVariant { long stored; boolean craftable; }
    private MaintenanceVariantService() { }
}
