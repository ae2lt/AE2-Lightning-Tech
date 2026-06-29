package com.moakiee.ae2lt.logic.batch;

import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.world.level.Level;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.service.CraftingService;

import com.moakiee.ae2lt.api.crafting.IBatchCraftingProvider;
import com.moakiee.ae2lt.overload.pattern.OverloadedProviderOnlyPatternDetails;

public final class BatchExecutor {
    private BatchExecutor() {
    }

    public static BatchRunResult runBatchOnly(int remainingOps,
                                              CraftingService cs,
                                              IEnergyService es,
                                              Level level,
                                              BatchJobView job,
                                              ListCraftingInventory inv,
                                              Map<IPatternDetails, IdentityHashMap<ICraftingProvider, Boolean>> batchedByTask,
                                              Runnable markDirty) {
        if (job == null) return BatchRunResult.EMPTY;

        var taskIter = job.taskIterator();
        if (!taskIter.hasNext()) return BatchRunResult.EMPTY;

        int totalPushed = 0;
        int consumedOps = 0;
        int opsBudget = remainingOps;
        if (opsBudget <= 0) return BatchRunResult.EMPTY;
        boolean dirty = false;

        while (taskIter.hasNext()) {
            var task = taskIter.next();
            long taskValue = task.getValue();
            if (taskValue <= 0) {
                taskIter.remove();
                continue;
            }

            var details = task.details();
            if (details instanceof OverloadedProviderOnlyPatternDetails) {
                continue;
            }

            var perTaskBatched = batchedByTask.computeIfAbsent(details, key -> new IdentityHashMap<>());

            var eligible = new java.util.ArrayList<EligibleProvider>();
            long availableBatchCapacity = 0;
            for (var provider : cs.getProviders(details)) {
                if (!(provider instanceof IBatchCraftingProvider batch)) continue;
                if (perTaskBatched.containsKey(provider)) continue;
                int capacity = batch.getBatchCapacity(details);
                if (capacity <= 0) continue;
                eligible.add(new EligibleProvider(batch, capacity));
                availableBatchCapacity += capacity;
                if (availableBatchCapacity >= Integer.MAX_VALUE) {
                    availableBatchCapacity = Integer.MAX_VALUE;
                    break;
                }
            }
            if (eligible.isEmpty() || availableBatchCapacity <= 0) continue;

            // Dispatch smaller-capacity providers first: the even split (leftover/remaining) grows
            // as remaining shrinks, so leaving the largest provider last lets it absorb the
            // remainder. Front-loading large providers caps them to the small even share and leaves
            // the batch under-filled.
            eligible.sort(java.util.Comparator.comparingInt(EligibleProvider::capacity));

            int copyBudget = BatchCpuAccounting.maxCopiesForCpuOps(opsBudget);
            if (copyBudget <= 0) {
                if (dirty) markDirty.run();
                return new BatchRunResult(totalPushed, consumedOps);
            }

            int budget = (int) Math.min(Math.min(taskValue, availableBatchCapacity), copyBudget);
            if (budget <= 0) continue;

            var result = ParallelBatchCpuHelper.bulkExtract(details, inv, budget);
            if (result == null) {
                continue;
            }

            int realCraft = result.actualCopies;
            double powerForReal = CraftingCpuHelper.calculatePatternPower(result.scaledInputs);
            double powerOne = realCraft > 0 ? powerForReal / realCraft : 0.0D;
            double availablePower = es.extractAEPower(powerForReal, Actionable.SIMULATE, PowerMultiplier.CONFIG);
            if (availablePower < powerForReal - 0.01D) {
                int affordable = powerOne > 0.0D ? (int) Math.floor(availablePower / powerOne) : 0;
                if (affordable <= 0) {
                    ParallelBatchCpuHelper.reinject(result, realCraft, inv);
                    if (dirty) markDirty.run();
                    return new BatchRunResult(totalPushed, consumedOps);
                }
                int scaleDown = realCraft - affordable;
                if (scaleDown > 0) {
                    ParallelBatchCpuHelper.reinject(result, scaleDown, inv);
                    realCraft = affordable;
                }
            }

            int initialRealCraft = realCraft;
            int leftover = realCraft;
            KeyCounter[] oneCopy = ParallelBatchCpuHelper.cloneSingleCopy(result);

            for (int i = 0; i < eligible.size() && leftover > 0; i++) {
                var batch = eligible.get(i).provider();
                int sliceCap = BatchCpuAccounting.maxCopiesForCpuOps(opsBudget);
                if (sliceCap <= 0) break;
                int remainingProviders = eligible.size() - i;
                int slice = Math.max(1, leftover / remainingProviders);
                slice = Math.min(slice, leftover);
                slice = Math.min(slice, sliceCap);
                slice = Math.min(slice, eligible.get(i).capacity()); // cap by this provider's reported capacity

                int subLeftover;
                try {
                    subLeftover = batch.pushBatch(details, oneCopy, slice);
                } catch (Throwable t) {
                    appeng.core.AELog.warn("[ae2lt] IBatchCraftingProvider %s threw during pushBatch; treating as full leftover. %s",
                            batch, t);
                    subLeftover = slice;
                }
                if (subLeftover < 0 || subLeftover > slice) {
                    appeng.core.AELog.warn("[ae2lt] IBatchCraftingProvider %s returned out-of-range leftover %d for slice=%d; treating as full leftover.",
                            batch, subLeftover, slice);
                    subLeftover = slice;
                }

                int dispatched = slice - subLeftover;
                if (dispatched <= 0) continue;

                ParallelBatchCpuHelper.markDispatched(result, dispatched);
                es.extractAEPower(powerOne * dispatched, Actionable.MODULATE, PowerMultiplier.CONFIG);
                ParallelBatchCpuHelper.registerExpectedOutputs(job, details, dispatched);
                dirty = true;

                int opsCost = BatchCpuAccounting.cpuOpsForCopies(dispatched);
                consumedOps += opsCost;
                opsBudget -= opsCost;

                long newValue = task.getValue() - dispatched;
                task.setValue(newValue);
                totalPushed += dispatched;
                leftover -= dispatched;

                if (initialRealCraft > 1) {
                    perTaskBatched.put((ICraftingProvider) batch, Boolean.TRUE);
                }

                if (newValue <= 0) {
                    taskIter.remove();
                    if (leftover > 0) {
                        ParallelBatchCpuHelper.reinject(result, leftover, inv);
                        leftover = 0;
                    }
                    if (opsBudget <= 0) {
                        if (dirty) markDirty.run();
                        return new BatchRunResult(totalPushed, consumedOps);
                    }
                    break;
                }

                if (opsBudget <= 0) {
                    if (leftover > 0) {
                        ParallelBatchCpuHelper.reinject(result, leftover, inv);
                        leftover = 0;
                    }
                    if (dirty) markDirty.run();
                    return new BatchRunResult(totalPushed, consumedOps);
                }
            }

            if (leftover > 0) {
                ParallelBatchCpuHelper.reinject(result, leftover, inv);
            }
        }

        if (dirty) markDirty.run();
        return new BatchRunResult(totalPushed, consumedOps);
    }

    private record EligibleProvider(IBatchCraftingProvider provider, int capacity) {
    }

    public record BatchRunResult(int dispatchedCopies, int consumedCpuOps) {
        public static final BatchRunResult EMPTY = new BatchRunResult(0, 0);
    }
}
