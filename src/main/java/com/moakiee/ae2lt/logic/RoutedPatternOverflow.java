package com.moakiee.ae2lt.logic;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

/**
 * Provider-owned pattern inputs that still have to reach their original target face.
 *
 * <p>A {@code null} face means "use the dispatch route's default face". Directional
 * patterns always store an explicit face. Keeping that distinction in the queue
 * prevents a retry from flattening AdvancedAE inputs onto one arbitrary side.
 */
final class RoutedPatternOverflow {

    record Entry(@Nullable Direction face, GenericStack stack) {
        Entry {
            if (stack == null || stack.what() == null || stack.amount() <= 0L) {
                throw new IllegalArgumentException("Overflow entries require a positive stack");
            }
        }

        Direction resolvedFace(Direction defaultFace) {
            return face != null ? face : defaultFace;
        }
    }

    @FunctionalInterface
    interface Inserter {
        long insert(Direction face, AEKey what, long amount);
    }

    @FunctionalInterface
    interface UnroutedFlusher {
        void flush(List<GenericStack> stacks);
    }

    private final List<Entry> entries;

    private RoutedPatternOverflow(List<Entry> entries) {
        this.entries = new ArrayList<>(entries);
    }

    static RoutedPatternOverflow unrouted(List<GenericStack> stacks) {
        var entries = new ArrayList<Entry>(stacks.size());
        for (var stack : stacks) {
            if (stack != null && stack.what() != null && stack.amount() > 0L) {
                entries.add(new Entry(null, stack));
            }
        }
        return new RoutedPatternOverflow(entries);
    }

    static RoutedPatternOverflow routed(List<Entry> entries) {
        return new RoutedPatternOverflow(entries);
    }

    List<Entry> snapshot() {
        return List.copyOf(entries);
    }

    boolean isEmpty() {
        return entries.isEmpty();
    }

    boolean hasExplicitFaces() {
        for (var entry : entries) {
            if (entry.face() != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Presents an unrouted queue in the legacy mutable-list form expected by
     * machine adapters, then imports their remaining stacks back into this queue.
     */
    boolean flushUnrouted(UnroutedFlusher flusher) {
        if (hasExplicitFaces()) {
            throw new IllegalStateException("A routed overflow cannot use an unrouted machine adapter");
        }

        long before = totalAmount(entries);
        var stacks = new ArrayList<GenericStack>(entries.size());
        for (var entry : entries) {
            stacks.add(entry.stack());
        }
        flusher.flush(stacks);

        var remaining = new ArrayList<Entry>(stacks.size());
        for (var stack : stacks) {
            if (stack != null && stack.what() != null && stack.amount() > 0L) {
                remaining.add(new Entry(null, stack));
            }
        }
        long after = totalAmount(remaining);
        if (after > before) {
            throw new IllegalStateException("Pattern overflow adapter increased the queued amount");
        }

        entries.clear();
        entries.addAll(remaining);
        return after < before;
    }

    /**
     * Attempts every queued key once. A blocked key does not prevent other keys or
     * faces from progressing, but no entry may be retried through a different face.
     */
    boolean flush(Direction defaultFace, Inserter inserter) {
        boolean progressed = false;
        for (int i = 0; i < entries.size();) {
            var entry = entries.get(i);
            var stack = entry.stack();
            long inserted = inserter.insert(
                    entry.resolvedFace(defaultFace),
                    stack.what(),
                    stack.amount());
            if (inserted < 0L || inserted > stack.amount()) {
                throw new IllegalStateException(
                        "Pattern overflow target returned an invalid insertion amount");
            }
            if (inserted >= stack.amount()) {
                entries.remove(i);
                progressed = true;
                continue;
            }
            if (inserted > 0L) {
                entries.set(i, new Entry(
                        entry.face(),
                        new GenericStack(stack.what(), stack.amount() - inserted)));
                progressed = true;
            }
            i++;
        }
        return progressed;
    }

    private static long totalAmount(List<Entry> entries) {
        long total = 0L;
        for (var entry : entries) {
            long amount = entry.stack().amount();
            total = Long.MAX_VALUE - total < amount
                    ? Long.MAX_VALUE
                    : total + amount;
        }
        return total;
    }
}
