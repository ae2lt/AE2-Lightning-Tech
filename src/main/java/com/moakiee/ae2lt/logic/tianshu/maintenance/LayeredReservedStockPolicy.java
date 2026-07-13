package com.moakiee.ae2lt.logic.tianshu.maintenance;

import appeng.api.stacks.AEKey;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Combines one Tianshu's maintenance-wide reserve floor with stricter per-rule additions. */
public final class LayeredReservedStockPolicy {
    private final ReservedStockRepository global;
    private final ReservedStockRepository additional;

    public LayeredReservedStockPolicy(
            ReservedStockRepository global, ReservedStockRepository additional) {
        this.global = global;
        this.additional = additional;
    }

    public boolean isEmpty() {
        return size(global) == 0 && size(additional) == 0;
    }

    public boolean groupsSecondaryVariants(AEKey key) {
        return groups(global, key) || groups(additional, key);
    }

    public long usablePreexistingStock(AEKey key, long snapshotAmount) {
        long usable = Math.max(0L, snapshotAmount);
        if (global != null) {
            usable = Math.min(usable, global.usablePreexistingStock(key, snapshotAmount));
        }
        if (additional != null) {
            usable = Math.min(usable, additional.usablePreexistingStock(key, snapshotAmount));
        }
        return usable;
    }

    public long usablePreexistingStock(
            AEKey key, long snapshotAmount, Map<AEKey, Long> groupSnapshot) {
        if (!groupsSecondaryVariants(key)) {
            return usablePreexistingStock(key, snapshotAmount);
        }

        var stock = normalizedGroup(key, snapshotAmount, groupSnapshot);
        var variants = stock.keySet().stream()
                .sorted(Comparator.comparing(LayeredReservedStockPolicy::stableKey))
                .toList();
        var exactCaps = new LinkedHashMap<AEKey, Long>();
        for (var variant : variants) {
            long amount = stock.get(variant);
            long cap = amount;
            cap = Math.min(cap, exactCap(global, variant, amount));
            cap = Math.min(cap, exactCap(additional, variant, amount));
            exactCaps.put(variant, cap);
        }

        var constraints = new ArrayList<GroupConstraint>();
        addGroupConstraint(constraints, global, variants, stock);
        addGroupConstraint(constraints, additional, variants, stock);

        var allocated = new LinkedHashMap<AEKey, Long>();
        for (var variant : variants) {
            long usable = exactCaps.get(variant);
            for (var constraint : constraints) {
                if (constraint.includes(variant)) {
                    usable = Math.min(usable, constraint.remaining());
                }
            }
            allocated.put(variant, usable);
            for (var constraint : constraints) {
                if (constraint.includes(variant)) constraint.consume(usable);
            }
        }
        return allocated.getOrDefault(key, 0L);
    }

    private static void addGroupConstraint(
            List<GroupConstraint> constraints,
            ReservedStockRepository repository,
            List<AEKey> variants,
            Map<AEKey, Long> stock) {
        if (repository == null) return;
        ReservedStockRepository.Entry group = null;
        for (var reservation : repository.reservations()) {
            if (reservation.mode() == ReservedStockMatchMode.IGNORE_SECONDARY
                    && variants.stream().anyMatch(variant -> sameGroup(reservation.key(), variant))) {
                group = reservation;
                break;
            }
        }
        if (group == null) return;

        var included = new ArrayList<AEKey>();
        long total = 0L;
        for (var variant : variants) {
            if (!sameGroup(group.key(), variant) || hasExact(repository, variant)) continue;
            included.add(variant);
            total = saturatingAdd(total, stock.getOrDefault(variant, 0L));
        }
        long capacity = group.amount() == ReservedStockRepository.INFINITE
                ? 0L : Math.max(0L, total - group.amount());
        constraints.add(new GroupConstraint(List.copyOf(included), capacity));
    }

    private static Map<AEKey, Long> normalizedGroup(
            AEKey key, long snapshotAmount, Map<AEKey, Long> groupSnapshot) {
        var result = new LinkedHashMap<AEKey, Long>();
        if (groupSnapshot != null) {
            groupSnapshot.forEach((variant, amount) -> {
                if (variant != null && sameGroup(key, variant)) {
                    result.put(variant, Math.max(0L, amount));
                }
            });
        }
        result.putIfAbsent(key, Math.max(0L, snapshotAmount));
        return result;
    }

    private static long exactCap(
            ReservedStockRepository repository, AEKey key, long stock) {
        if (repository == null) return stock;
        for (var reservation : repository.reservations()) {
            if (reservation.mode() != ReservedStockMatchMode.EXACT
                    || !reservation.key().equals(key)) continue;
            return reservation.amount() == ReservedStockRepository.INFINITE
                    ? 0L : Math.max(0L, stock - reservation.amount());
        }
        return stock;
    }

    private static boolean hasExact(ReservedStockRepository repository, AEKey key) {
        if (repository == null) return false;
        return repository.reservations().stream().anyMatch(reservation ->
                reservation.mode() == ReservedStockMatchMode.EXACT
                        && reservation.key().equals(key));
    }

    private static boolean groups(ReservedStockRepository repository, AEKey key) {
        return repository != null && repository.groupsSecondaryVariants(key);
    }

    private static int size(ReservedStockRepository repository) {
        return repository != null ? repository.size() : 0;
    }

    private static boolean sameGroup(AEKey left, AEKey right) {
        return left != null && right != null && left.dropSecondary().equals(right.dropSecondary());
    }

    private static String stableKey(AEKey key) {
        return key.getId() + "|" + String.valueOf(key.getPrimaryKey()) + "|" + key.hashCode();
    }

    private static long saturatingAdd(long left, long right) {
        return left >= Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static final class GroupConstraint {
        private final List<AEKey> included;
        private long remaining;

        private GroupConstraint(List<AEKey> included, long remaining) {
            this.included = included;
            this.remaining = remaining;
        }

        private boolean includes(AEKey key) { return included.contains(key); }
        private long remaining() { return remaining; }
        private void consume(long amount) { remaining = Math.max(0L, remaining - amount); }
    }
}
