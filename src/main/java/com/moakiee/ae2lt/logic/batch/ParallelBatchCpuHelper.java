package com.moakiee.ae2lt.logic.batch;

import java.util.Arrays;
import java.util.HashMap;

import org.jetbrains.annotations.Nullable;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ListCraftingInventory;

public final class ParallelBatchCpuHelper {
    private ParallelBatchCpuHelper() {
    }

    /**
     * Extracts up to {@code maxCraft} homogeneous copies in O(input slots).
     *
     * <p>Each pattern input group chooses a single concrete item key. If no
     * single variant can satisfy a slot, this returns {@code null} so the CPU
     * can fall back to AE2's vanilla one-copy substitution path.
     */
    @Nullable
    public static BulkResult bulkExtract(IPatternDetails details, ListCraftingInventory inv, int maxCraft) {
        if (maxCraft <= 0) return null;

        var inputs = details.getInputs();
        int slots = inputs.length;
        var chosenKeys = new AEKey[slots];
        var perCopyUnits = new long[slots];
        var availCache = new long[slots];

        for (int i = 0; i < slots; i++) {
            var input = inputs[i];
            long multiplier = input.getMultiplier();
            var possibles = input.getPossibleInputs();

            if (possibles.length == 1) {
                var only = possibles[0];
                if (only.what() == null) return null;
                long perCopy = only.amount() * multiplier;
                if (perCopy <= 0) return null;
                long avail = inv.extract(only.what(), Long.MAX_VALUE, Actionable.SIMULATE);
                if (avail < perCopy) return null;
                chosenKeys[i] = only.what();
                perCopyUnits[i] = perCopy;
                availCache[i] = avail;
                continue;
            }

            AEKey bestKey = null;
            long bestPerCopy = 0;
            long bestAvail = 0;
            long bestCopies = 0;
            for (var possible : possibles) {
                if (possible.what() == null) continue;
                long perCopy = possible.amount() * multiplier;
                if (perCopy <= 0) continue;
                long avail = inv.extract(possible.what(), Long.MAX_VALUE, Actionable.SIMULATE);
                long canDo = avail / perCopy;
                if (canDo > bestCopies) {
                    bestKey = possible.what();
                    bestPerCopy = perCopy;
                    bestAvail = avail;
                    bestCopies = canDo;
                    if (bestCopies >= maxCraft) break;
                }
            }
            if (bestKey == null || bestCopies <= 0) return null;
            chosenKeys[i] = bestKey;
            perCopyUnits[i] = bestPerCopy;
            availCache[i] = bestAvail;
        }

        long actual = maxCraft;
        boolean hasCollision = false;
        outer:
        for (int i = 1; i < slots; i++) {
            for (int j = 0; j < i; j++) {
                if (chosenKeys[i].equals(chosenKeys[j])) {
                    hasCollision = true;
                    break outer;
                }
            }
        }

        HashMap<AEKey, Long> totalPerCopy = null;
        if (!hasCollision) {
            for (int i = 0; i < slots; i++) {
                long canDo = availCache[i] / perCopyUnits[i];
                if (canDo < actual) actual = canDo;
                if (actual <= 0) return null;
            }
        } else {
            totalPerCopy = new HashMap<>(slots * 2);
            for (int i = 0; i < slots; i++) {
                totalPerCopy.merge(chosenKeys[i], perCopyUnits[i], Long::sum);
            }
            boolean[] visited = new boolean[slots];
            for (int i = 0; i < slots; i++) {
                if (visited[i]) continue;
                visited[i] = true;
                long perBatch = totalPerCopy.get(chosenKeys[i]);
                long canDo = perBatch > 0 ? availCache[i] / perBatch : 0;
                if (canDo < actual) actual = canDo;
                if (actual <= 0) return null;
                for (int j = i + 1; j < slots; j++) {
                    if (!visited[j] && chosenKeys[j].equals(chosenKeys[i])) {
                        visited[j] = true;
                    }
                }
            }
        }

        if (actual <= 0 || actual > Integer.MAX_VALUE) return null;

        if (!hasCollision) {
            long[] extracted = new long[slots];
            for (int i = 0; i < slots; i++) {
                long need = perCopyUnits[i] * actual;
                long got = inv.extract(chosenKeys[i], need, Actionable.MODULATE);
                extracted[i] = got;
                if (got < need) {
                    for (int j = 0; j <= i; j++) {
                        if (extracted[j] > 0) {
                            inv.insert(chosenKeys[j], extracted[j], Actionable.MODULATE);
                        }
                    }
                    return null;
                }
            }
        } else {
            var perKeyExtracted = new HashMap<AEKey, Long>(totalPerCopy.size() * 2);
            for (var entry : totalPerCopy.entrySet()) {
                long need = entry.getValue() * actual;
                long got = inv.extract(entry.getKey(), need, Actionable.MODULATE);
                perKeyExtracted.put(entry.getKey(), got);
                if (got < need) {
                    for (var rollback : perKeyExtracted.entrySet()) {
                        if (rollback.getValue() > 0) {
                            inv.insert(rollback.getKey(), rollback.getValue(), Actionable.MODULATE);
                        }
                    }
                    return null;
                }
            }
        }

        var scaled = new KeyCounter[slots];
        for (int i = 0; i < slots; i++) {
            scaled[i] = new KeyCounter();
            long amount = perCopyUnits[i] * actual;
            if (amount > 0) {
                scaled[i].add(chosenKeys[i], amount);
            }
        }

        return new BulkResult(scaled, (int) actual, chosenKeys, perCopyUnits);
    }

    public static void reinject(BulkResult result, int leftoverCopies, ListCraftingInventory inv) {
        if (leftoverCopies <= 0) return;
        for (int i = 0; i < result.scaledInputs.length; i++) {
            long amount = result.perCopyUnits[i] * leftoverCopies;
            if (amount > 0 && result.keys[i] != null) {
                inv.insert(result.keys[i], amount, Actionable.MODULATE);
                result.scaledInputs[i].remove(result.keys[i], amount);
            }
        }
    }

    public static void registerExpectedOutputs(BatchJobView job, IPatternDetails details, int dispatched) {
        if (dispatched <= 0) return;

        var waitingFor = job.waitingFor();
        for (var output : details.getOutputs()) {
            waitingFor.insert(output.what(), output.amount() * (long) dispatched, Actionable.MODULATE);
        }

        for (var input : details.getInputs()) {
            var possibles = input.getPossibleInputs();
            if (possibles.length == 0) continue;
            AEKey containerKey = input.getRemainingKey(possibles[0].what());
            if (containerKey != null) {
                long count = input.getMultiplier() * (long) dispatched;
                waitingFor.insert(containerKey, count, Actionable.MODULATE);
                job.addContainerMaxItems(count, containerKey.getType());
            }
        }
    }

    public static KeyCounter[] cloneSingleCopy(BulkResult result) {
        var copy = new KeyCounter[result.scaledInputs.length];
        for (int i = 0; i < copy.length; i++) {
            copy[i] = new KeyCounter();
            if (result.perCopyUnits[i] > 0 && result.keys[i] != null) {
                copy[i].add(result.keys[i], result.perCopyUnits[i]);
            }
        }
        return copy;
    }

    public static KeyCounter[] copySlice(BulkResult result, int sliceCount) {
        var slice = new KeyCounter[result.scaledInputs.length];
        for (int i = 0; i < slice.length; i++) {
            slice[i] = new KeyCounter();
            long amount = Math.max(0, sliceCount) * result.perCopyUnits[i];
            if (amount > 0 && result.keys[i] != null) {
                slice[i].add(result.keys[i], amount);
            }
        }
        return slice;
    }

    public static void markDispatched(BulkResult result, int dispatchedCopies) {
        if (dispatchedCopies <= 0) return;
        for (int i = 0; i < result.scaledInputs.length; i++) {
            long amount = result.perCopyUnits[i] * dispatchedCopies;
            if (amount > 0 && result.keys[i] != null) {
                result.scaledInputs[i].remove(result.keys[i], amount);
            }
        }
    }

    public static final class BulkResult {
        public final KeyCounter[] scaledInputs;
        public final int actualCopies;
        final AEKey[] keys;
        final long[] perCopyUnits;

        public BulkResult(KeyCounter[] scaledInputs, int actualCopies, AEKey[] keys, long[] perCopyUnits) {
            this.scaledInputs = scaledInputs;
            this.actualCopies = actualCopies;
            this.keys = Arrays.copyOf(keys, keys.length);
            this.perCopyUnits = Arrays.copyOf(perCopyUnits, perCopyUnits.length);
        }
    }
}
