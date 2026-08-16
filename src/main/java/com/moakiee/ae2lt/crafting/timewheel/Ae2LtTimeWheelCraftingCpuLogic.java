package com.moakiee.ae2lt.crafting.timewheel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import com.google.common.base.Preconditions;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.features.IPlayerRegistry;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.AELog;
import appeng.core.network.ClientboundPacket;
import appeng.core.network.clientbound.CraftingJobStatusPacket;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.crafting.execution.ElapsedTimeTracker;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.hooks.ticking.TickHandler;
import appeng.me.service.CraftingService;

import com.moakiee.thunderbolt.core.crafting.batch.BatchExecutor;
import com.moakiee.thunderbolt.core.crafting.batch.BatchCpuAccounting;
import com.moakiee.thunderbolt.api.crafting.batch.BatchJobView;
import com.moakiee.thunderbolt.api.crafting.batch.BatchTaskHandle;
import com.moakiee.thunderbolt.core.crafting.batch.ParallelBatchCpuHelper;
import com.moakiee.thunderbolt.core.crafting.batch.TickProviderDispatchSchedule;
import com.moakiee.ae2lt.crafting.runtime.api.CraftingTaskPriorities;
import com.moakiee.thunderbolt.core.crafting.support.CraftingPatternDelegates;
import com.moakiee.thunderbolt.core.crafting.loop.CraftingTaskPersistenceDefinition;
import com.moakiee.thunderbolt.core.crafting.loop.ISeedPreservingCraftingTask;
import com.moakiee.thunderbolt.core.crafting.loop.ReusableSeedPattern;
import com.moakiee.ae2lt.mixin.thunderbolt.accessor.ElapsedTimeTrackerAccessor;
import com.moakiee.ae2lt.overload.runtime.cpu.OverloadClaimResult;
import com.moakiee.ae2lt.overload.runtime.cpu.OverloadConsumerCredit;
import com.moakiee.ae2lt.overload.runtime.cpu.OverloadCpuStateManager;
import com.moakiee.ae2lt.overload.runtime.cpu.OverloadPatternReference;
import com.moakiee.ae2lt.overload.runtime.cpu.OverloadReusableSeedMetadata;
import com.moakiee.ae2lt.overload.runtime.model.MatchMode;
import com.moakiee.ae2lt.overload.runtime.pattern.OverloadedProviderOnlyPatternDetails;
import com.moakiee.thunderbolt.core.crafting.loop.PatternFiringExpander;
import com.moakiee.ae2lt.crafting.runtime.ExecuteLoopPattern;
import com.moakiee.thunderbolt.core.crafting.plan.LoopCraftingPlan;
import com.moakiee.thunderbolt.core.crafting.planner.Sat;

public final class Ae2LtTimeWheelCraftingCpuLogic {
    private static final int WHEEL_SIZE = 64;
    private static final int WHEEL_MASK = WHEEL_SIZE - 1;
    private static final int MAX_TASK_PROBES_PER_TICK = 262_144;
    private static final int RETRY_DELAY_TICKS = 4;
    private static final int PARKED_TASK_SAFETY_DELAY_TICKS = 32;
    private static final String TAG_INVENTORY = "inventory";
    private static final String TAG_JOB = "job";
    private static final String TAG_OVERLOAD_STATE = "ae2ltOverloadState";
    private static final String TAG_SEED_RETURN_QUOTA = "reusableSeedReturnQuota";
    private static final String TAG_SEED_RETURN_QUOTA_FINALIZED = "reusableSeedReturnQuotaFinalized";
    private static final String TAG_RETAINED_FINAL_OUTPUTS = "retainedLoopFinalOutputs";
    private static final String TAG_PENDING_REQUESTER_OUTPUTS = "pendingRequesterFinalOutputs";

    private static final String NBT_LINK = "link";
    private static final String NBT_PLAYER_ID = "playerId";
    private static final String NBT_FINAL_OUTPUT = "finalOutput";
    private static final String NBT_WAITING_FOR = "waitingFor";
    private static final String NBT_TIME_TRACKER = "timeTracker";
    private static final String NBT_REMAINING_AMOUNT = "remainingAmount";
    private static final String NBT_TASKS = "tasks";
    private static final String NBT_SUSPENDED = "suspended";
    private static final String NBT_SOFT_CANCELLING = "softCancelling";
    private static final String NBT_CLOSED_LOOP_JOB = "closedLoopJob";
    private static final String NBT_CRAFTING_PROGRESS = "#craftingProgress";
    private static final String NBT_INPUT_SEED = "#inputSeed";
    private static final String NBT_INITIAL_SEED = "#initialSeed";
    private static final String NBT_OUTPUT_SEED = "#outputSeed";
    private static final String NBT_SEED_CONSUMER = "#seedConsumer";
    private static final String NBT_OUTPUT_SEED_CREDITS = "#outputSeedCredits";
    private static final String NBT_SHARED_OUTPUT_SEED_CREDITS = "#sharedOutputSeedCredits";
    private static final String NBT_CREDIT_CONSUMER = "consumer";
    private static final String NBT_CREDIT_ITEMS = "items";

    private final TimeWheelCraftingCPU cpu;
    private final ListCraftingInventory inventory = new ListCraftingInventory(this::postChange);
    private final Set<Consumer<AEKey>> listeners = new HashSet<>();
    private final Map<IPatternDetails, IdentityHashMap<ICraftingProvider, Boolean>> batchedByTask = new HashMap<>();
    private final ArrayDeque<IPatternDetails>[] taskWheel = createWheel();
    private final Set<IPatternDetails> queuedTasks = Collections.newSetFromMap(new IdentityHashMap<>());
    // Parked tasks are indexed by AEKey#getPrimaryKey() (item id, ignoring components/damage), not the
    // exact declared input key: providers like the matter warping matrix return container items as a
    // DIFFERENT exact key (e.g. a durability tool at damage+1), and an exact-key index would never see
    // the return and leave the task to the 32-tick safety poll alone.
    private final TimeWheelTaskWakeIndex<IPatternDetails> tasksParkedByMissingKey = new TimeWheelTaskWakeIndex<>();
    private final Set<AEKey> batchedStatusChanges = new HashSet<>();
    private final List<AEKey> statusChangeScratch = new ArrayList<>();
    private final Set<IPatternDetails> nonBatchTasksThisTick = Collections.newSetFromMap(new IdentityHashMap<>());
    private final KeyCounter scratchExpectedOutputs = new KeyCounter();
    private final KeyCounter scratchExpectedContainerItems = new KeyCounter();
    private final SingleTaskBatchJobView scratchBatchJobView = new SingleTaskBatchJobView();
    // Pattern power depends only on the (fixed) input amounts of a pattern, so it is constant across
    // every copy pushed for a given task. Caching it removes calculatePatternPower from the hot
    // per-copy dispatch loop (it was ~7% of the CPU tick in profiling). Keyed by pattern identity;
    // cleared on every job lifecycle transition below.
    private final Map<IPatternDetails, Double> patternPowerCache = new IdentityHashMap<>();
    private final TickProviderDispatchSchedule standaloneDispatchSchedule = new TickProviderDispatchSchedule();
    private final KeyCounter seedReturnQuota = new KeyCounter();
    private final KeyCounter retainedFinalOutputs = new KeyCounter();
    private final KeyCounter pendingRequesterOutputs = new KeyCounter();
    private final LoopSeedLedgerBook loopSeedLedgers = new LoopSeedLedgerBook();

    @Nullable
    private TimeWheelJob job;
    @Nullable
    private CompoundTag pendingJobTag;
    @Nullable
    private CompoundTag pendingOverloadTag;
    private long lastModifiedOnTick = TickHandler.instance().getCurrentTick();
    private long waitingKeysModifiedOnTick = TickHandler.instance().getCurrentTick();
    private long schedulerTick = Long.MIN_VALUE;
    private long batchTick = Long.MIN_VALUE;
    private int wheelCursor;
    @Nullable
    private IPatternDetails preferredTask;
    private boolean queueRebuildNeeded = true;
    private boolean cantStoreItems;
    private boolean batchingStatusChanges;
    private boolean seedReturnQuotaFinalized;

    public Ae2LtTimeWheelCraftingCpuLogic(TimeWheelCraftingCPU cpu) {
        this.cpu = cpu;
    }

    public ICraftingSubmitResult trySubmitJob(IGrid grid, ICraftingPlan plan, IActionSource src,
                                              @Nullable ICraftingRequester requester) {
        resolvePendingLoad();
        if (this.job != null || this.pendingJobTag != null) {
            return CraftingSubmitResult.CPU_BUSY;
        }
        if (!cpu.isActive()) {
            return CraftingSubmitResult.CPU_OFFLINE;
        }
        if (cpu.getAvailableStorage() < plan.bytes()) {
            return CraftingSubmitResult.CPU_TOO_SMALL;
        }

        if (!inventory.list.isEmpty()) {
            AELog.warn("Time wheel crafting CPU inventory is not empty yet a job was submitted.");
        }

        var loopPlan = plan instanceof LoopCraftingPlan loop ? loop : null;
        var seedRequirements = loopPlan != null
                ? copyToCounter(loopPlan.totalReusableSeeds()) : new KeyCounter();
        var hostSeedAllocations = loopPlan != null
                ? loopPlan.hostReusableSeedAllocations() : List.<LoopCraftingPlan.HostReusableSeedAllocation>of();
        var playerId = src.player()
                .map(p -> p instanceof ServerPlayer serverPlayer ? IPlayerRegistry.getPlayerId(serverPlayer) : null)
                .orElse(null);
        var craftId = UUID.randomUUID();
        var linkCpu = new CraftingLink(
                CraftingCpuHelper.generateLinkData(craftId, requester == null, false), cpu);
        var candidateJob = new TimeWheelJob(plan, this::postChange, linkCpu, playerId);
        loopSeedLedgers.initialize(candidateJob.loopPatterns());

        var adjustedUsedItems = copyCounter(plan.usedItems());
        var hostSeeds = new KeyCounter();
        boolean hostShortfall = false;
        for (var allocation : hostSeedAllocations) {
            long requested = allocation.amount();
            AEKey extractionKey = allocation.bootstrap()
                    ? allocation.actualKey() : allocation.plannedKey();
            var extractedVariants = cpu.getHost().extractReusableSeedVariants(
                    extractionKey,
                    requested,
                    actual -> allocation.bootstrap()
                            ? allocation.actualKey().equals(actual)
                            : loopPlan != null
                                    && loopPlan.acceptsReusableSeedVariant(allocation, actual),
                    Actionable.MODULATE);
            var offered = new KeyCounter();
            long offeredAmount = 0L;
            for (var actual : extractedVariants) {
                long amount = Math.min(actual.getLongValue(), requested - offeredAmount);
                if (amount > 0) {
                    offered.add(actual.getKey(), amount);
                    offeredAmount = addSaturated(offeredAmount, amount);
                }
                // A conforming host never returns more than requested. Preserve the surplus even
                // if a third-party implementation violates that contract or its capacity changes
                // between extraction and rollback.
                long surplus = actual.getLongValue() - Math.max(0L, amount);
                if (surplus > 0) {
                    long returned = cpu.getHost().insertReusableSeed(
                            actual.getKey(), surplus, Actionable.MODULATE);
                    long held = surplus - Math.max(0L, returned);
                    if (held > 0) {
                        inventory.insert(actual.getKey(), held, Actionable.MODULATE);
                        hostSeeds.add(actual.getKey(), held);
                    }
                }
            }
            var acceptedVariants = allocation.bootstrap()
                    ? offered
                    : loopSeedLedgers.assignHostVariantsForGroup(
                            allocation.reusableSeedGroupId(), allocation.sharedPool(),
                            allocation.plannedKey(), offered);
            long extracted = 0L;
            for (var actual : offered) {
                long accepted = Math.min(
                        actual.getLongValue(), acceptedVariants.get(actual.getKey()));
                if (accepted > 0) {
                    inventory.insert(actual.getKey(), accepted, Actionable.MODULATE);
                    hostSeeds.add(actual.getKey(), accepted);
                    extracted = addSaturated(extracted, accepted);
                }
                long rejected = actual.getLongValue() - accepted;
                if (rejected > 0) {
                    long returned = cpu.getHost().insertReusableSeed(
                            actual.getKey(), rejected, Actionable.MODULATE);
                    if (returned < rejected) {
                        // Preserve items even if a host concurrently lost capacity. They remain
                        // ordinary CPU inventory and are not counted as a reusable assignment.
                        long held = rejected - Math.max(0L, returned);
                        inventory.insert(actual.getKey(), held, Actionable.MODULATE);
                        hostSeeds.add(actual.getKey(), held);
                    }
                }
            }
            if (extracted < requested) {
                adjustedUsedItems.add(
                        allocation.bootstrap() ? allocation.actualKey() : allocation.plannedKey(),
                        requested - extracted);
                hostShortfall = true;
            }
        }

        ICraftingPlan extractionPlan = hostShortfall
                ? new UsedItemsOverridePlan(plan, adjustedUsedItems) : plan;
        var missingIngredient = CraftingCpuHelper.tryExtractInitialItems(extractionPlan, grid, inventory, src);
        if (missingIngredient != null) {
            rollbackHostSeeds(hostSeeds);
            loopSeedLedgers.clear();
            return CraftingSubmitResult.missingIngredient(missingIngredient);
        }

        this.job = candidateJob;
        seedReturnQuota.clear();
        retainedFinalOutputs.clear();
        pendingRequesterOutputs.clear();
        for (var entry : seedRequirements) {
            seedReturnQuota.add(entry.getKey(), entry.getLongValue());
        }
        seedReturnQuotaFinalized = false;
        patternPowerCache.clear();
        markWaitingKeysChanged();
        cpu.updateOutput(plan.finalOutput());
        cpu.markDirty();
        rebuildTaskWheel();

        notifyJobOwner(job, CraftingJobStatusPacket.Status.STARTED);

        if (requester != null) {
            var linkReq = new CraftingLink(CraftingCpuHelper.generateLinkData(craftId, false, true), requester);
            var craftingService = (CraftingService) grid.getCraftingService();
            craftingService.addLink(linkCpu);
            craftingService.addLink(linkReq);
            return CraftingSubmitResult.successful(linkReq);
        }

        return CraftingSubmitResult.successful(null);
    }

    public void tickCraftingLogic(IEnergyService energyService, CraftingService craftingService) {
        standaloneDispatchSchedule.beginTick(TickHandler.instance().getCurrentTick());
        try {
            tickCraftingLogic(
                    energyService,
                    craftingService,
                    cpu.getSuccessfulDispatchesPerTick(),
                    cpu.hasUnboundedBatch() ? Long.MAX_VALUE : cpu.getMaxCopiesPerTick(),
                    standaloneDispatchSchedule);
        } finally {
            finishPhysicalSchedulingTick();
        }
    }

    public TickUsage tickCraftingLogic(IEnergyService energyService,
                                       CraftingService craftingService,
                                       int maxOps,
                                       long maxCopies) {
        standaloneDispatchSchedule.beginTick(TickHandler.instance().getCurrentTick());
        try {
            return tickCraftingLogic(
                    energyService,
                    craftingService,
                    maxOps,
                    maxCopies,
                    standaloneDispatchSchedule);
        } finally {
            finishPhysicalSchedulingTick();
        }
    }

    public TickUsage tickCraftingLogic(IEnergyService energyService,
                                       CraftingService craftingService,
                                       int maxOps,
                                       long maxCopies,
                                       TickProviderDispatchSchedule dispatchSchedule) {
        resolvePendingLoad();
        if (this.pendingJobTag != null) {
            return TickUsage.EMPTY;
        }
        if (!cpu.isActive()) {
            return TickUsage.EMPTY;
        }

        long now = TickHandler.instance().getCurrentTick();
        if (now != batchTick) {
            batchTick = now;
            batchedByTask.clear();
            nonBatchTasksThisTick.clear();
        }

        cantStoreItems = false;
        if (this.job == null) {
            storeItems();
            if (!this.inventory.list.isEmpty()) {
                cantStoreItems = true;
            }
            return TickUsage.EMPTY;
        }

        var activeJob = this.job;
        if (activeJob.softCancelling) {
            finishSoftCancelIfReady(activeJob);
            return TickUsage.EMPTY;
        }

        if (activeJob.link.isCanceled()) {
            if (!activeJob.softCancelling) {
                if (hasReusableSeedPattern(activeJob)) beginSoftCancel(activeJob);
                else cancel();
            }
            return TickUsage.EMPTY;
        }

        flushPendingRequesterOutputs(activeJob);
        // Requester callbacks are synchronous and may cancel or otherwise finish the current link.
        // Do not continue dispatching against the stale job object after such a callback.
        if (this.job != activeJob) {
            return TickUsage.EMPTY;
        }
        if (activeJob.link.isCanceled()) {
            if (hasReusableSeedPattern(activeJob)) beginSoftCancel(activeJob);
            else cancel();
            return TickUsage.EMPTY;
        }
        flushUnusedRetainedFinalOutputs(activeJob);
        if (this.job != activeJob || activeJob.link.isCanceled()) {
            return TickUsage.EMPTY;
        }
        recoverTerminalFinalOutputFromInventory(activeJob);
        if (this.job != activeJob || activeJob.link.isCanceled()) {
            return TickUsage.EMPTY;
        }
        if (activeJob.remainingAmount <= 0) {
            finishSuccessfulIfReady(activeJob);
            return TickUsage.EMPTY;
        }

        if (activeJob.suspended) {
            return TickUsage.EMPTY;
        }

        var level = cpu.getLevel();
        if (level == null) {
            return TickUsage.EMPTY;
        }

        int remainingOperations = Math.min(
                Math.max(0, maxOps),
                cpu.getSuccessfulDispatchesPerTick());
        long remainingCopies = Math.min(
                Math.max(0L, maxCopies),
                cpu.hasUnboundedBatch() ? Long.MAX_VALUE : cpu.getMaxCopiesPerTick());
        return executeCraftingBudgeted(
                remainingOperations,
                remainingCopies,
                craftingService,
                energyService,
                level,
                dispatchSchedule);
    }

    public int executeCrafting(int maxOps, CraftingService craftingService, IEnergyService energyService,
                               Level level) {
        standaloneDispatchSchedule.beginTick(TickHandler.instance().getCurrentTick());
        try {
            return executeCraftingBudgeted(
                    maxOps,
                    cpu.hasUnboundedBatch() ? Long.MAX_VALUE : cpu.getMaxCopiesPerTick(),
                    craftingService,
                    energyService,
                    level,
                    standaloneDispatchSchedule).successfulDispatches();
        } finally {
            finishPhysicalSchedulingTick();
        }
    }

    private TickUsage executeCraftingBudgeted(int maxOps,
                                              long requestedCopyLimit,
                                              CraftingService craftingService,
                                              IEnergyService energyService,
                                              Level level,
                                              TickProviderDispatchSchedule dispatchSchedule) {
        var activeJob = this.job;
        if (activeJob == null || maxOps <= 0 || requestedCopyLimit <= 0L) {
            return TickUsage.EMPTY;
        }

        prepareScheduler(activeJob);

        int usedOps = 0;
        long usedCopies = 0L;
        long cpuCopyLimit = cpu.hasUnboundedBatch() ? Long.MAX_VALUE : cpu.getMaxCopiesPerTick();
        long copyLimit = Math.min(cpuCopyLimit, requestedCopyLimit);
        int probes = 0;
        int probeBudget = (int) Math.min(Math.max(1024L, (long) maxOps * 2L), MAX_TASK_PROBES_PER_TICK);

        beginStatusChangeBatch();
        try {
            while (usedOps < maxOps && usedCopies < copyLimit && probes < probeBudget) {
                var details = pollDueTask(activeJob);
                if (details == null) {
                    break;
                }

                var task = activeJob.tasks.get(details);
                if (task == null || task.value <= 0) {
                    removeTask(activeJob, details);
                    postPatternOutputsChange(details);
                    continue;
                }

                probes++;
                int remainingOps = maxOps - usedOps;
                long remainingCopies = copyLimit == Long.MAX_VALUE
                        ? Long.MAX_VALUE : copyLimit - usedCopies;
                var batchResult = runBatchForTask(
                        details,
                        remainingOps,
                        remainingCopies,
                        craftingService,
                        energyService,
                        level,
                        dispatchSchedule);
                if (batchResult.consumedCpuOps() > 0) {
                    usedOps += batchResult.consumedCpuOps();
                    usedCopies = saturatingAdd(usedCopies, batchResult.dispatchedCopies());
                    preferTaskWhilePending(activeJob, details);
                    rescheduleIfStillPending(activeJob, details, 0);
                    continue;
                }

                int ordinaryBudget = (int) Math.min(remainingOps, remainingCopies);
                var bulk = pushBulkForTask(
                        activeJob,
                        task,
                        details,
                        ordinaryBudget,
                        craftingService,
                        energyService,
                        dispatchSchedule);
                if (bulk != null) {
                    usedOps += bulk.dispatched();
                    usedCopies = saturatingAdd(usedCopies, bulk.dispatched());
                    if (bulk.dispatched() > 0) {
                        preferTaskWhilePending(activeJob, details);
                    }
                    rescheduleIfStillPending(activeJob, details, bulk.retryDelayTicks());
                    continue;
                }

                var outcome = pushOnePattern(
                        activeJob,
                        task,
                        details,
                        craftingService,
                        energyService,
                        level,
                        dispatchSchedule);
                if (outcome == DispatchOutcome.PUSHED) {
                    usedOps++;
                    usedCopies = saturatingAdd(usedCopies, 1L);
                    unparkTask(details);
                    preferTaskWhilePending(activeJob, details);
                    rescheduleIfStillPending(activeJob, details, 0);
                } else {
                    rescheduleFailedTask(activeJob, details, outcome);
                }
            }
        } finally {
            endStatusChangeBatch();
        }

        if (job == activeJob) flushUnusedRetainedFinalOutputs(activeJob);

        return new TickUsage(usedOps, usedCopies);
    }

    public record TickUsage(int successfulDispatches, long dispatchedCopies) {
        public static final TickUsage EMPTY = new TickUsage(0, 0L);
    }

    private BatchExecutor.BatchRunResult runBatchForTask(IPatternDetails details,
                                                         int remainingOps,
                                                         long remainingCopies,
                                                         CraftingService craftingService,
                                                         IEnergyService energyService,
                                                         Level level,
                                                         TickProviderDispatchSchedule dispatchSchedule) {
        var activeJob = this.job;
        if (activeJob == null) {
            return BatchExecutor.BatchRunResult.EMPTY;
        }
        // Overload outputs must be registered per dispatched pattern so returned variants can be
        // split between the requester and the CPU seed pool. BatchExecutor bypasses that
        // registration path, therefore overload patterns intentionally stay on pushOnePattern.
        if (CraftingPatternDelegates.forProviderLookup(details)
                instanceof OverloadedProviderOnlyPatternDetails) {
            nonBatchTasksThisTick.add(details);
            return BatchExecutor.BatchRunResult.EMPTY;
        }
        if (details instanceof ExecuteLoopPattern loop
                && loop.requiresActualSeedKeyTracking()) {
            nonBatchTasksThisTick.add(details);
            return BatchExecutor.BatchRunResult.EMPTY;
        }
        if (nonBatchTasksThisTick.contains(details)) {
            return BatchExecutor.BatchRunResult.EMPTY;
        }

        var result = BatchExecutor.runBatchOnly(
                remainingOps,
                BatchCpuAccounting.Mode.SUCCESSFUL_DISPATCH,
                craftingService,
                energyService,
                scratchBatchJobView.bind(activeJob, details),
                inventory,
                batchedByTask,
                () -> {
                    cpu.markDirty();
                    postPatternOutputsChange(details);
                },
                reservedSeedStock(details),
                1,
                remainingCopies,
                cpu.hasUnboundedBatch(),
                dispatchSchedule);
        if (result.dispatchedCopies() > 0) {
            // T is a per-virtual-CPU tick budget, not a per-call width. Let the time wheel revisit
            // the task while its private T and the physical CPU's shared successful-dispatch
            // budget remain. Reusable seeds naturally stop until their concrete key returns.
            batchedByTask.remove(details);
            boolean sharedSeedBatch = (details instanceof ExecuteLoopPattern loop
                            ? loop.delegate() : details)
                    instanceof com.moakiee.thunderbolt.core.crafting.batch.SharedBatchInputPattern;
            recordLoopPatternDispatch(
                    details, result.dispatchedCopies(), sharedSeedBatch);
        }
        if (!result.shouldRetryBatchThisTick()) {
            nonBatchTasksThisTick.add(details);
        }
        return result;
    }

    private DispatchOutcome pushOnePattern(TimeWheelJob activeJob,
                                           TaskProgress task,
                                           IPatternDetails details,
                                           CraftingService craftingService,
                                           IEnergyService energyService,
                                           Level level,
                                           TickProviderDispatchSchedule dispatchSchedule) {
        if (hasAmbiguousOverloadOutput(details)) {
            return DispatchOutcome.RETRY_LATER;
        }

        var expectedOutputs = scratchExpectedOutputs;
        var expectedContainerItems = scratchExpectedContainerItems;
        // Scratch counters are always emptied on exit (both branches below), so they are clean on
        // entry and need no leading clear. This halves the per-copy KeyCounter clearing that showed
        // up as ~7% of the CPU tick in profiling.
        var extractionInventory = reservedCraftingInventory(details);
        KeyCounter[] craftingContainer = CraftingCpuHelper.extractPatternInputs(
                details, extractionInventory, level, expectedOutputs, expectedContainerItems);
        if (craftingContainer == null) {
            clearScratchCounter(expectedOutputs);
            clearScratchCounter(expectedContainerItems);
            return DispatchOutcome.RETRY_MISSING_INPUT;
        }

        boolean pushed = false;
        try {
            // Overload output routing must also know unchanged container/tool remainders. They
            // may already satisfy a P2 credit and must not be assigned again to an ID_ONLY output
            // slot, even when the reusable input itself is strict and has only one candidate.
            var actualLoopSeedResolution = details instanceof ExecuteLoopPattern loop
                    ? loop.resolveActualInputSeedUses(craftingContainer) : null;
            if (actualLoopSeedResolution != null && !actualLoopSeedResolution.complete()) {
                return DispatchOutcome.RETRY_MISSING_INPUT;
            }
            var actualLoopSeedInput = actualLoopSeedResolution != null
                    ? actualLoopSeedResolution.uses() : null;
            if (details instanceof ExecuteLoopPattern loop
                    && !loopSeedLedgers.canRouteActualSeedUses(loop, actualLoopSeedInput)) {
                return DispatchOutcome.RETRY_MISSING_INPUT;
            }
            if (details instanceof ExecuteLoopPattern loop) {
                var remainderCredits = loopSeedLedgers.previewRemainderCredits(
                        loop, 1L, false, actualLoopSeedInput);
                if (hasAmbiguousOverloadOutput(details, remainderCredits)) {
                    return DispatchOutcome.RETRY_LATER;
                }
            }
            var patternPower = patternPowerFor(details, craftingContainer);
            for (var resolvedProvider : providersForSinglePush(
                    craftingService, details, dispatchSchedule)) {
                var provider = resolvedProvider.provider();
                if (provider.isBusy()) {
                    continue;
                }

                if (energyService.extractAEPower(patternPower, Actionable.SIMULATE,
                        PowerMultiplier.CONFIG) < patternPower - 0.01D) {
                    return DispatchOutcome.RETRY_NO_POWER;
                }

                if (!tryPushPattern(resolvedProvider, craftingContainer, dispatchSchedule)) {
                    continue;
                }

                pushed = true;
                craftingContainer = null;
                energyService.extractAEPower(patternPower, Actionable.MODULATE, PowerMultiplier.CONFIG);
                var remainderLoopCredits = recordLoopPatternDispatch(
                        details, 1L, false, actualLoopSeedInput);
                recordPushedPattern(
                        activeJob, details, expectedOutputs, expectedContainerItems, 1L,
                        remainderLoopCredits);

                consumeTaskCopies(activeJob, details, 1L);
                return DispatchOutcome.PUSHED;
            }
                return DispatchOutcome.RETRY_SOON;
        } finally {
            if (!pushed && craftingContainer != null) {
                CraftingCpuHelper.reinjectPatternInputs(extractionInventory, craftingContainer);
            }
            clearScratchCounter(expectedOutputs);
            clearScratchCounter(expectedContainerItems);
        }
    }

    /**
     * Fast path for homogeneous (non-overload) patterns. Instead of paying AE2's full per-copy
     * extraction (template resolution via {@code getValidItemTemplates}, input extraction and
     * pattern-power computation) once per copy, this extracts every copy it intends to push this
     * visit in a single {@link ParallelBatchCpuHelper#bulkExtract} call and then hands the
     * pre-resolved copies to the (non-batch) providers one {@code pushPattern} at a time.
     *
     * @return the visit result, or {@code null} when the pattern needs AE2's one-copy substitution
     *         path (overload patterns or non-homogeneous/fuzzy inputs).
     */
    @Nullable
    private BulkPush pushBulkForTask(TimeWheelJob activeJob,
                                     TaskProgress task,
                                     IPatternDetails details,
                                     int maxCopies,
                                     CraftingService craftingService,
                                     IEnergyService energyService,
                                     TickProviderDispatchSchedule dispatchSchedule) {
        if (CraftingPatternDelegates.forProviderLookup(details)
                instanceof OverloadedProviderOnlyPatternDetails) {
            return null;
        }
        if (task.value <= 0 || maxCopies <= 0) {
            return new BulkPush(0, 1);
        }
        if (details instanceof ExecuteLoopPattern loop
                && loop.requiresActualSeedKeyTracking()) {
            return null;
        }
        // Skip the (SIMULATE + MODULATE) extraction entirely if nothing can accept a push right
        // now. Busy providers typically free up within a tick, so retry on the next one (same
        // cadence as the vanilla path's RETRY_SOON, NOT the 4-tick energy/missing-input backoff).
        var providers = providersForSinglePush(
                craftingService, details, dispatchSchedule).iterator();
        var firstProvider = nextFreeProvider(providers);
        if (firstProvider == null) {
            return new BulkPush(0, 1);
        }

        int budget = (int) Math.min(task.value, (long) maxCopies);
        var result = ParallelBatchCpuHelper.bulkExtract(
                details, inventory, budget, false, reservedSeedStock(details));
        if (result == null) {
            return null;
        }

        // This non-batch path is explicitly bounded by the int maxCopies argument above.
        int actual = (int) result.actualCopies;
        // The first clone doubles as the power probe and the first container handed to a provider.
        KeyCounter[] pending = ParallelBatchCpuHelper.cloneSingleCopy(result);
        double powerOne = patternPowerFor(details, pending);

        // One SIMULATE for the whole visit (instead of one per copy) caps how many copies we can
        // afford; each dispatched copy still MODULATEs individually, so accounting matches the
        // vanilla path. BatchExecutor batches its energy checks the same way.
        int affordable = actual;
        double wanted = powerOne * actual;
        if (wanted > 0) {
            double avail = energyService.extractAEPower(wanted, Actionable.SIMULATE, PowerMultiplier.CONFIG);
            if (avail < wanted - 0.01D) {
                affordable = (int) Math.min((long) actual, (long) Math.floor(avail / powerOne));
            }
        }
        if (affordable <= 0) {
            ParallelBatchCpuHelper.reinject(result, actual, inventory);
            return new BulkPush(0, RETRY_DELAY_TICKS);
        }

        int dispatched = 0;
        boolean freeProviderRejected = false;
        try {
            var resolvedProvider = firstProvider;
            while (resolvedProvider != null && dispatched < affordable) {
                var provider = resolvedProvider.provider();
                while (dispatched < affordable && !provider.isBusy()) {
                    if (pending == null) {
                        pending = ParallelBatchCpuHelper.cloneSingleCopy(result);
                    }
                    if (!tryPushPattern(resolvedProvider, pending, dispatchSchedule)) {
                        // A rejecting provider must not consume the container, so the clone stays
                        // valid and is reused for the next provider instead of re-cloning.
                        freeProviderRejected = true;
                        break;
                    }
                    pending = null; // ownership transferred to the provider
                    energyService.extractAEPower(powerOne, Actionable.MODULATE, PowerMultiplier.CONFIG);
                    ParallelBatchCpuHelper.markDispatched(result, 1);
                    dispatched++;
                }
                resolvedProvider = nextFreeProvider(providers);
            }
        } finally {
            int leftover = actual - dispatched;
            if (leftover > 0) {
                ParallelBatchCpuHelper.reinject(result, leftover, inventory);
            }
        }

        if (dispatched > 0) {
            var jobView = scratchBatchJobView.bind(activeJob, details);
            ParallelBatchCpuHelper.registerExpectedOutputs(jobView, details, result, dispatched);
            recordLoopPatternDispatch(details, dispatched, false);
            consumeTaskCopies(activeJob, details, dispatched);
            cpu.markDirty();
            // Energy-capped visits back off on the energy cadence; otherwise re-poll immediately
            // (delay 0) so the remaining copies keep filling providers this tick.
            return new BulkPush(dispatched, affordable < actual ? RETRY_DELAY_TICKS : 0);
        }

        // Every rejecting provider-pattern pair is already removed from this tick's shared
        // schedule. Falling through to the one-copy path would only repeat extraction and scan an
        // empty candidate set, so park this task until the next tick instead.
        if (freeProviderRejected) {
            return new BulkPush(0, 1);
        }
        // Every provider turned busy since the pre-scan: plain contention, retry next tick.
        return new BulkPush(0, 1);
    }

    @Nullable
    private ResolvedProvider nextFreeProvider(Iterator<ResolvedProvider> providers) {
        while (providers.hasNext()) {
            var resolved = providers.next();
            if (!resolved.provider().isBusy()) {
                return resolved;
            }
        }
        return null;
    }

    private boolean tryPushPattern(
            ResolvedProvider resolvedProvider,
            KeyCounter[] inputs,
            TickProviderDispatchSchedule dispatchSchedule) {
        var provider = resolvedProvider.provider();
        try {
            if (!provider.pushPattern(resolvedProvider.pattern(), inputs)) {
                dispatchSchedule.recordFailure(resolvedProvider.pattern(), provider);
                return false;
            }
        } catch (Throwable t) {
            AELog.warn("[ae2lt] ICraftingProvider %s threw during pushPattern; blocking this pattern for the current tick. %s",
                    provider, t);
            dispatchSchedule.recordFailure(resolvedProvider.pattern(), provider);
            return false;
        }
        dispatchSchedule.recordSuccess(resolvedProvider.pattern(), provider);
        return true;
    }

    public long insert(AEKey what, long amount, Actionable type) {
        var activeJob = this.job;
        if (what == null || activeJob == null || amount <= 0) {
            return 0;
        }

        // Fast path: waitingKeys mirrors waitingFor's positive keys exactly, and every overload
        // acceptance path requires a pending entry (hasAnyPending). A CPU that is not waiting on
        // this key accepts nothing, so skip the per-path synchronized manager probes entirely.
        if (!activeJob.waitingKeys.contains(what)
                && !OverloadCpuStateManager.INSTANCE.hasAnyPending(this)) {
            if (type == Actionable.MODULATE) {
                finishSuccessfulIfReady(activeJob);
            }
            return 0;
        }

        long returned = 0;
        long remaining = amount;
        long simulatedExactOverload = 0L;
        long simulatedStrictPrefix = 0L;
        long exactOverloadWaiting = OverloadCpuStateManager.INSTANCE
                .getRemainingForExactKey(this, what);
        if (exactOverloadWaiting > 0) {
            // Native waiting contains both real STRICT demand and the exact templates mirrored by
            // ID_ONLY pending slots. Protect the proven STRICT excess first; otherwise an exact
            // ID_ONLY return can steal an unchanged container remainder that already owns a P2
            // credit. The remaining mirrored prefix is then consumed through overload state.
            long strictPrefix = OverloadInsertAccounting.strictPrefixBeforeExactOverload(
                    remaining, activeJob.waitingFor.list.get(what), exactOverloadWaiting);
            if (strictPrefix > 0) {
                returned += acceptStrictWaitingItem(
                        activeJob, what, strictPrefix, type);
                remaining -= strictPrefix;
                if (type == Actionable.SIMULATE) simulatedStrictPrefix = strictPrefix;
                if (job != activeJob || remaining <= 0) {
                    if (type == Actionable.MODULATE && job == activeJob) {
                        finishSuccessfulIfReady(activeJob);
                    }
                    return returned;
                }
            }
        }
        if (OverloadCpuStateManager.INSTANCE.hasExactPending(this, what)) {
            var overload = acceptOverloadWaitingItem(activeJob, what, remaining, type);
            returned += overload.accepted();
            remaining -= overload.claimed();
            if (type == Actionable.SIMULATE) simulatedExactOverload = overload.claimed();
            if (job != activeJob || remaining <= 0) {
                if (type == Actionable.MODULATE && job == activeJob) {
                    finishSuccessfulIfReady(activeJob);
                }
                return returned;
            }
        }

        // In SIMULATE the overload claim above cannot mutate AE2's exact waitingFor entry. Probe
        // against the original offer, then remove the already-simulated overload prefix. Probing
        // only `remaining` before subtracting would under-count ordinary strict demand and could
        // claim the same overload entry again below.
        long simulatedOverlap = addSaturated(
                simulatedExactOverload, simulatedStrictPrefix);
        long strictProbeAmount = OverloadInsertAccounting.strictProbeAmount(
                remaining, simulatedOverlap);
        long strictMatched = activeJob.waitingFor.extract(
                what, strictProbeAmount, Actionable.SIMULATE);
        strictMatched = OverloadInsertAccounting.strictMatchAfterExactOverload(
                remaining, strictMatched, simulatedOverlap);
        long acceptedStrict = Math.min(remaining, strictMatched);
        if (acceptedStrict > 0) {
            long accepted = acceptStrictWaitingItem(
                    activeJob, what, acceptedStrict, type);
            returned += accepted;
        }
        remaining -= strictMatched;

        if (remaining <= 0
                || !OverloadInsertAccounting.mayClaimOverloadRemainder(simulatedExactOverload)
                || !OverloadCpuStateManager.INSTANCE.hasAnyPending(this)) {
            if (type == Actionable.MODULATE) finishSuccessfulIfReady(activeJob);
            return returned;
        }

        var overload = acceptOverloadWaitingItem(activeJob, what, remaining, type);
        returned += overload.accepted();
        if (type == Actionable.MODULATE && job == activeJob) finishSuccessfulIfReady(activeJob);
        return returned;
    }

    private OverloadInsert acceptOverloadWaitingItem(
            TimeWheelJob activeJob, AEKey what, long amount, Actionable type) {
        if (amount <= 0) return OverloadInsert.EMPTY;
        var preview = OverloadCpuStateManager.INSTANCE.claim(
                this, what, amount, Actionable.SIMULATE,
                (consumer, expected) -> loopSeedLedgers.acceptsReturnedVariant(
                        consumer, expected, what));
        if (!preview.claimedAnything()) {
            return OverloadInsert.EMPTY;
        }

        long retainedRequester = 0L;
        long requesterAccepted = 0L;
        var limited = preview;
        if (!activeJob.softCancelling && preview.claimedForRequester() > 0) {
            retainedRequester = retainableFinalOutputAmount(
                    activeJob, what, preview.claimedForRequester());
            long requesterLimit = Math.min(
                    Math.max(0L, preview.claimedForRequester() - retainedRequester),
                    activeJob.remainingAmount);
            requesterAccepted = requesterLimit > 0
                    ? activeJob.link.insert(what, requesterLimit, type) : 0L;
            limited = preview.partitionRequester(requesterLimit, requesterAccepted);
        }
        var claims = limited;
        if (type == Actionable.MODULATE) {
            claims = OverloadCpuStateManager.INSTANCE.commitPreview(this, claims);
        }
        if (!claims.claimedAnything()) return OverloadInsert.EMPTY;

        long accepted = 0L;
        if (type == Actionable.MODULATE) {
            deductClaimedWaitingFor(activeJob, claims);
            rekeyOverloadReusableSeeds(what, claims);
            if (activeJob.softCancelling) {
                long claimed = claims.claimedAmount();
                decrementItems(activeJob.timeTracker, claimed, what.getType());
                inventory.insert(what, claimed, Actionable.MODULATE);
                accepted += claimed;
                if (activeJob.waitingKeys.isEmpty()
                        && !OverloadCpuStateManager.INSTANCE.hasAnyPending(this)) {
                    finishSoftCancelIfReady(activeJob);
                }
            } else {
                retainedRequester = Math.min(
                        retainedRequester, overloadPublicInventory(claims));
                accepted += applyInventoryClaims(activeJob, what, claims);
                markRetainedRequesterClaim(what, retainedRequester);
                accepted += applyRequesterClaims(activeJob, what, claims);
            }
            cpu.markDirty();
        } else {
            accepted += claims.claimedAmount();
        }

        return new OverloadInsert(claims.claimedAmount(), accepted);
    }

    private record OverloadInsert(long claimed, long accepted) {
        private static final OverloadInsert EMPTY = new OverloadInsert(0L, 0L);
    }

    private long acceptStrictWaitingItem(TimeWheelJob activeJob, AEKey what, long amount, Actionable type) {
        if (type == Actionable.MODULATE) {
            decrementItems(activeJob.timeTracker, amount, what.getType());
            extractWaitingFor(activeJob, what, amount);
            cpu.markDirty();
        }

        if (activeJob.softCancelling) {
            if (type == Actionable.MODULATE) {
                inventory.insert(what, amount, Actionable.MODULATE);
                if (activeJob.waitingKeys.isEmpty()
                        && !OverloadCpuStateManager.INSTANCE.hasAnyPending(this)) {
                    finishSoftCancelIfReady(activeJob);
                }
            }
            return amount;
        }

        long inserted = amount;
        if (what.matches(activeJob.finalOutput)) {
            inserted = acceptFinalOutputWithReusableSeed(activeJob, what, amount, type);
        } else if (type == Actionable.MODULATE) {
            inventory.insert(what, amount, Actionable.MODULATE);
            wakeSchedulerForReturnedInput(what);
        }

        return inserted;
    }

    private long acceptFinalOutputWithReusableSeed(
            TimeWheelJob activeJob, AEKey what, long amount, Actionable type) {
        long seedQuota = Math.max(
                seedReturnQuota.get(what), loopSeedLedgers.totalReserved(what));
        long accepted = 0L;
        long remaining = amount;
        // Seed ownership always wins when the returned key is also the requested output. Only the
        // amount left after restoring the seed quota may satisfy the final-output request.
        long reserved = reserveReturnedSeed(what, remaining, seedQuota, type);
        accepted += reserved;
        remaining -= reserved;

        // Public output remains ordinary job output. While another loop copy can consume it, keep
        // it in the CPU and borrow it only at the next successful dispatch; it is not promoted into
        // the permanent seed account. This is what turns A -> 2A into logarithmic dispatch depth.
        long retained = retainFinalOutputForLoop(activeJob, what, remaining, type);
        accepted += retained;
        remaining -= retained;

        long finalOffer = Math.min(remaining, activeJob.remainingAmount);
        long delivered = offerToRequester(activeJob, what, finalOffer, type);
        accepted += delivered;
        // A standalone AE2 crafting link intentionally has no requester, so link.insert always
        // returns zero. The produced item must fall through to ordinary ME storage, but the CPU
        // still has to count the offered final output as completed. Vanilla CraftingCpuLogic uses
        // the same split between storage acceptance and job-progress accounting.
        long completedFinalOutput = FinalOutputProgress.completedAmount(
                activeJob.link.isStandalone(), finalOffer, delivered);
        long deferredRequesterOutput = FinalOutputProgress.deferredRequesterAmount(
                activeJob.link.isStandalone(), finalOffer, delivered);
        if (deferredRequesterOutput > 0) {
            // Requester callbacks can legitimately accept only part of an output. In particular,
            // a requester that writes to the same NetworkStorage is called while that storage is
            // already iterating and receives zero from AE2's recursion guard. Keep the physical
            // remainder in this CPU and retry it on a later tick instead of losing its waiting
            // state. Standalone jobs deliberately retain vanilla's fall-through-to-ME behavior.
            if (type == Actionable.MODULATE) {
                inventory.insert(what, deferredRequesterOutput, Actionable.MODULATE);
                // A requester may cancel the link from inside insertCraftedItems. In that case the
                // remainder is ordinary canceled-job inventory and must not create a stale retry
                // record after finishJob has already cleared the queue.
                if (job == activeJob && !activeJob.link.isCanceled()) {
                    pendingRequesterOutputs.add(what, deferredRequesterOutput);
                }
                cpu.markDirty();
            }
            accepted += deferredRequesterOutput;
        }
        // Only the physical tail beyond the final-output offer is seed/excess.
        long tail = remaining - finalOffer;

        // Any deterministic output beyond both the requested final amount and the one retained seed
        // is a normal byproduct/net surplus and belongs in CPU inventory for ordinary return to ME.
        if (tail > 0) {
            if (type == Actionable.MODULATE) {
                inventory.insert(what, tail, Actionable.MODULATE);
                // Tasks parked on this exact key can consume CPU inventory; without a wake they
                // wait out the full parked-task safety delay.
                wakeSchedulerForReturnedInput(what);
            }
            accepted += tail;
        }

        if (type == Actionable.MODULATE && completedFinalOutput > 0) {
            finishCompletedFinalOutput(activeJob, what, completedFinalOutput);
        } else if (type == Actionable.MODULATE && reserved > 0) {
            wakeSchedulerForReturnedInput(what);
        }
        return accepted;
    }

    private long reserveReturnedSeed(
            AEKey what, long amount, long quota, Actionable type) {
        if (amount <= 0 || quota <= 0) return 0L;
        long alreadyHeld = inventory.extract(what, Long.MAX_VALUE, Actionable.SIMULATE);
        long reserved = Math.min(amount, Math.max(0L, quota - alreadyHeld));
        if (reserved > 0 && type == Actionable.MODULATE) {
            inventory.insert(what, reserved, Actionable.MODULATE);
            wakeSchedulerForReturnedInput(what);
        }
        return reserved;
    }

    private void finishCompletedFinalOutput(TimeWheelJob activeJob, AEKey what, long completed) {
        if (job != activeJob || completed <= 0) return;
        postChange(what);
        activeJob.remainingAmount = Math.max(0L, activeJob.remainingAmount - completed);
        capPendingRequesterOutputsToRemaining(activeJob.remainingAmount, what);
        if (activeJob.remainingAmount > 0) {
            cpu.updateOutput(new GenericStack(activeJob.finalOutput.what(), activeJob.remainingAmount));
        } else {
            // The requested output may arrive before an external executor returns its catalyst.
            // Keep the job/link alive until every reusable seed is physically back in this CPU.
            cpu.updateOutput(null);
        }
    }

    private void finishSuccessfulIfReady(TimeWheelJob activeJob) {
        if (job != activeJob || activeJob.softCancelling) return;
        // Also repairs persisted jobs written by versions that could leave deferred credits after
        // the final demand had already reached zero.
        capPendingRequesterOutputsToRemaining(activeJob.remainingAmount, null);
        if (activeJob.remainingAmount > 0
                || !activeJob.tasks.isEmpty()
                || !activeJob.waitingKeys.isEmpty()
                || !pendingRequesterOutputs.isEmpty()
                || OverloadCpuStateManager.INSTANCE.hasAnyPending(this)) return;
        finalizeSeedReturnQuota();
        if (!returnReusableSeedsToHost()) return;
        finishJob(true);
        cpu.updateOutput(null);
    }

    private void finishSoftCancelIfReady(TimeWheelJob activeJob) {
        if (job != activeJob || !activeJob.softCancelling
                || !activeJob.waitingKeys.isEmpty()
                || OverloadCpuStateManager.INSTANCE.hasAnyPending(this)) return;
        finalizeSeedReturnQuota();
        if (!returnReusableSeedsToHost()) return;
        finishJob(false);
        cpu.updateOutput(null);
    }

    /** Moves returned seeds into the private host drive before the link is allowed to finish. */
    private boolean returnReusableSeedsToHost() {
        if (seedReturnQuota.isEmpty()) return true;
        for (var seed : seedReturnQuota) {
            if (inventory.extract(seed.getKey(), Long.MAX_VALUE, Actionable.SIMULATE)
                    < seed.getLongValue()) return false;
        }
        var returnedSeeds = new ArrayList<GenericStack>();
        for (var seed : seedReturnQuota) {
            returnedSeeds.add(new GenericStack(seed.getKey(), seed.getLongValue()));
        }
        boolean changed = false;
        for (var seed : returnedSeeds) {
            long held = inventory.extract(seed.what(), Long.MAX_VALUE, Actionable.SIMULATE);
            long acceptable = cpu.getHost().insertReusableSeed(
                    seed.what(), seed.amount(), Actionable.SIMULATE);
            long transferable = ReusableSeedStorageProgress.transferable(
                    seed.amount(), held, acceptable);
            if (transferable <= 0) continue;
            long removed = inventory.extract(
                    seed.what(), transferable, Actionable.MODULATE);
            long inserted = removed > 0
                    ? cpu.getHost().insertReusableSeed(
                            seed.what(), removed, Actionable.MODULATE) : 0L;
            if (inserted < removed) {
                inventory.insert(seed.what(), removed - inserted, Actionable.MODULATE);
            }
            if (inserted > 0) {
                removeUpTo(seedReturnQuota, seed.what(), inserted);
                changed = true;
            }
        }
        if (!seedReturnQuota.isEmpty()) cantStoreItems = true;
        if (changed) cpu.markDirty();
        return seedReturnQuota.isEmpty();
    }

    private void finalizeSeedReturnQuota() {
        if (seedReturnQuotaFinalized) return;
        seedReturnQuota.clear();
        for (var entry : loopSeedLedgers.positiveSnapshot().entrySet()) {
            seedReturnQuota.add(entry.getKey(), entry.getValue());
        }
        seedReturnQuotaFinalized = true;
        cpu.markDirty();
    }

    private void finishJob(boolean success) {
        var activeJob = this.job;
        if (activeJob == null) {
            return;
        }

        OverloadCpuStateManager.INSTANCE.clear(this);

        if (success) {
            activeJob.link.markDone();
        } else {
            activeJob.link.cancel();
        }

        clearWaitingFor(activeJob);
        for (var entry : activeJob.tasks.entrySet()) {
            postPatternOutputsChange(entry.getKey());
        }

        notifyJobOwner(
                activeJob,
                success ? CraftingJobStatusPacket.Status.FINISHED : CraftingJobStatusPacket.Status.CANCELLED);

        this.job = null;
        seedReturnQuotaFinalized = false;
        retainedFinalOutputs.clear();
        pendingRequesterOutputs.clear();
        clearLoopSeedState();
        patternPowerCache.clear();
        clearTaskWheel();
        storeItems();
    }

    public void cancel() {
        if (job == null) {
            return;
        }
        if (!job.softCancelling && hasReusableSeedPattern(job)) {
            beginSoftCancel(job);
            return;
        }
        // Stop intercepting reusable seeds. Anything already held by the CPU is real returned
        // content and must fall through to ordinary ME storage after the job is closed.
        seedReturnQuota.clear();
        seedReturnQuotaFinalized = false;
        clearLoopSeedState();
        cpu.updateOutput(null);
        finishJob(false);
    }

    /**
     * Starts the normal safe-cancellation path and releases everything that can
     * be returned immediately. Closed-loop jobs that are still waiting for
     * reusable seeds remain persistent instead of discarding their ledger.
     */
    void tryReleaseContents() {
        cancel();
        if (job == null) {
            storeItems();
        }
    }

    private boolean hasReusableSeedPattern(TimeWheelJob activeJob) {
        return !seedReturnQuota.isEmpty() || activeJob.closedLoopJob;
    }

    private void beginSoftCancel(TimeWheelJob activeJob) {
        activeJob.softCancelling = true;
        activeJob.suspended = false;
        for (var details : List.copyOf(activeJob.tasks.keySet())) {
            removeTask(activeJob, details);
            postPatternOutputsChange(details);
        }
        clearTaskWheel();
        cpu.updateOutput(null);
        cpu.markDirty();
        if (activeJob.waitingKeys.isEmpty()
                && !OverloadCpuStateManager.INSTANCE.hasAnyPending(this)) {
            finishSoftCancelIfReady(activeJob);
        }
    }

    public void prepareForRemoval() {
        if (this.job != null) {
            // A removed CPU cannot remain in the first-stage "wait for seed" state. Removal is the
            // destructive second cancellation: stop tracking late returns, cancel the link and
            // return/drop all content that is already physically in the CPU.
            seedReturnQuota.clear();
            seedReturnQuotaFinalized = false;
            clearLoopSeedState();
            finishJob(false);
        }
        this.pendingJobTag = null;
        this.pendingOverloadTag = null;
        OverloadCpuStateManager.INSTANCE.clear(this);
        seedReturnQuota.clear();
        pendingRequesterOutputs.clear();
        seedReturnQuotaFinalized = false;
        clearLoopSeedState();
        clearTaskWheel();
        cpu.updateOutput(null);
        cantStoreItems = false;
    }

    public void addStoredDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        prepareForRemoval();
        for (var entry : this.inventory.list) {
            if (entry.getLongValue() > 0) {
                entry.getKey().addDrops(entry.getLongValue(), drops, level, pos);
            }
        }
    }

    public void clearRemovedContent() {
        prepareForRemoval();
        this.inventory.clear();
        this.inventory.list.removeEmptySubmaps();
    }

    public void storeItems() {
        Preconditions.checkState(job == null, "CPU should not have a job to prevent re-insertion when dumping items");
        if (this.inventory.list.isEmpty()) {
            return;
        }

        var grid = cpu.getGrid();
        if (grid == null) {
            return;
        }

        var storage = grid.getStorageService().getInventory();
        for (var entry : this.inventory.list) {
            postChange(entry.getKey());
            long intercept = Math.min(entry.getLongValue(), seedReturnQuota.get(entry.getKey()));
            long seedInserted = intercept > 0
                    ? cpu.getHost().insertReusableSeed(entry.getKey(), intercept, Actionable.MODULATE)
                    : 0L;
            if (seedInserted > 0) {
                removeUpTo(seedReturnQuota, entry.getKey(), seedInserted);
                entry.setValue(entry.getLongValue() - seedInserted);
            }
            var inserted = storage.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE, cpu.getSrc());
            entry.setValue(entry.getLongValue() - inserted);
        }
        this.inventory.list.removeZeros();
        if (this.inventory.list.isEmpty()) {
            seedReturnQuota.clear();
            pendingRequesterOutputs.clear();
            clearLoopSeedState();
        }
        cpu.markDirty();
    }

    private static KeyCounter copyToCounter(Map<AEKey, Long> source) {
        var result = new KeyCounter();
        for (var entry : source.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null && entry.getValue() > 0) {
                result.add(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    /** Positive ledger entries are hidden unless this loop task declares the key as inputSeed. */
    private Map<AEKey, Long> reservedSeedStock(IPatternDetails details) {
        UUID ownConsumer = null;
        java.util.function.Predicate<AEKey> allowedSeedInput = ignored -> false;
        ExecuteLoopPattern ownLoop = null;
        if (details instanceof ExecuteLoopPattern loopPattern) {
            ownLoop = loopPattern;
            ownConsumer = loopPattern.seedConsumerId();
            allowedSeedInput = loopPattern::isInputSeedKey;
        }
        var ledgerReservations = loopSeedLedgers.reservationView(
                ownConsumer,
                allowedSeedInput,
                details instanceof ExecuteLoopPattern loop
                        && loop.hasSingleSeedInputPerMember());
        var allowedLoop = ownLoop;
        return new java.util.AbstractMap<>() {
            @Override
            public Long get(Object key) {
                if (!(key instanceof AEKey aeKey)) return null;
                long reserved = ledgerReservations.getOrDefault(aeKey, 0L);
                // Deferred requester output is already completed physical output. It must never be
                // borrowed as another recipe input while the requester is temporarily unable to
                // accept it; only retainedFinalOutputs is intentionally consumable by its loop.
                reserved = addSaturated(reserved, pendingRequesterOutputs.get(aeKey));
                if (allowedLoop == null || !allowedLoop.isInputSeedKey(aeKey)) {
                    reserved = addSaturated(reserved, retainedFinalOutputs.get(aeKey));
                }
                return reserved > 0 ? reserved : null;
            }

            @Override
            public Set<Entry<AEKey, Long>> entrySet() {
                return Set.of();
            }
        };
    }

    private appeng.crafting.inv.ICraftingInventory reservedCraftingInventory(
            IPatternDetails details) {
        return !loopSeedLedgers.hasReservations()
                && retainedFinalOutputs.isEmpty()
                && pendingRequesterOutputs.isEmpty()
                ? inventory
                : new ReservedCraftingInventory(inventory, reservedSeedStock(details));
    }

    private Map<UUID, KeyCounter> recordLoopPatternDispatch(
            IPatternDetails details,
            long copies,
            boolean sharedBatch) {
        return recordLoopPatternDispatch(details, copies, sharedBatch, null);
    }

    private Map<UUID, KeyCounter> recordLoopPatternDispatch(
            IPatternDetails details,
            long copies,
            boolean sharedBatch,
            @Nullable List<ExecuteLoopPattern.ActualSeedUse> actualInputSeed) {
        if (!(details instanceof ExecuteLoopPattern loopPattern)
                || copies <= 0) {
            return Map.of();
        }
        var changedCredits = loopSeedLedgers.recordDispatch(
                loopPattern, copies, sharedBatch, actualInputSeed);
        consumeRetainedFinalOutput(loopPattern, copies, sharedBatch, actualInputSeed);
        cpu.markDirty();
        return changedCredits;
    }

    private void consumeRetainedFinalOutput(
            ExecuteLoopPattern pattern,
            long copies,
            boolean sharedBatch,
            @Nullable List<ExecuteLoopPattern.ActualSeedUse> actualInputSeed) {
        if (retainedFinalOutputs.isEmpty()) return;
        if (actualInputSeed != null) {
            for (var use : actualInputSeed) {
                removeUpTo(retainedFinalOutputs, use.actual(), use.amount());
            }
            return;
        }
        long scale = sharedBatch ? 1L : copies;
        for (var input : pattern.inputSeed()) {
            removeUpTo(retainedFinalOutputs, input.getKey(),
                    multiplySaturated(input.getLongValue(), scale));
        }
    }

    private long pendingLoopSeedDemand(TimeWheelJob activeJob, AEKey key) {
        if (activeJob == null || key == null) return 0L;
        long demand = 0L;
        for (var task : activeJob.tasks.entrySet()) {
            if (!(task.getKey() instanceof ExecuteLoopPattern loop)
                    || task.getValue().value <= 0) continue;
            long perCopy = loop.inputSeedAmountFor(key);
            if (perCopy <= 0) continue;
            demand = addSaturated(demand,
                    multiplySaturated(perCopy, task.getValue().value));
        }
        return demand;
    }

    private long retainFinalOutputForLoop(
            TimeWheelJob activeJob, AEKey what, long amount, Actionable type) {
        long retained = retainableFinalOutputAmount(activeJob, what, amount);
        if (retained > 0 && type == Actionable.MODULATE) {
            retainLoopFinalOutput(what, retained);
        }
        return retained;
    }

    private long retainableFinalOutputAmount(
            TimeWheelJob activeJob, AEKey what, long amount) {
        if (amount <= 0 || activeJob == null || what == null
                || activeJob.finalOutput == null
                || !what.dropSecondary().equals(activeJob.finalOutput.what().dropSecondary())) {
            return 0L;
        }
        long demand = pendingLoopSeedDemand(activeJob, what);
        long alreadyRetained = 0L;
        for (var retained : retainedFinalOutputs) {
            if (sharesPendingLoopConsumer(activeJob, what, retained.getKey())) {
                alreadyRetained = addSaturated(alreadyRetained, retained.getLongValue());
            }
        }
        return Math.min(amount, Math.max(0L, demand - alreadyRetained));
    }

    private boolean sharesPendingLoopConsumer(
            TimeWheelJob activeJob, AEKey left, AEKey right) {
        if (activeJob == null || left == null || right == null) return false;
        if (left.equals(right)) return true;
        for (var task : activeJob.tasks.entrySet()) {
            if (!(task.getKey() instanceof ExecuteLoopPattern loop)
                    || task.getValue().value <= 0) continue;
            if (loop.inputSeedAmountFor(left) > 0 && loop.inputSeedAmountFor(right) > 0) {
                return true;
            }
        }
        return false;
    }

    private void retainLoopFinalOutput(AEKey what, long amount) {
        if (what == null || amount <= 0) return;
        inventory.insert(what, amount, Actionable.MODULATE);
        retainedFinalOutputs.add(what, amount);
        wakeSchedulerForReturnedInput(what);
        cpu.markDirty();
    }

    private void flushPendingRequesterOutputs(TimeWheelJob activeJob) {
        if (activeJob == null || activeJob.link.isStandalone()
                || activeJob.softCancelling) {
            return;
        }
        capPendingRequesterOutputsToRemaining(activeJob.remainingAmount, null);
        if (pendingRequesterOutputs.isEmpty()) return;

        var pending = new ArrayList<GenericStack>();
        for (var entry : pendingRequesterOutputs) {
            if (entry.getLongValue() > 0) {
                pending.add(new GenericStack(entry.getKey(), entry.getLongValue()));
            }
        }

        for (var entry : pending) {
            if (job != activeJob || activeJob.link.isCanceled()) return;

            long held = inventory.extract(entry.what(), Long.MAX_VALUE, Actionable.SIMULATE);
            long reusableReserve = Math.max(
                    seedReturnQuota.get(entry.what()),
                    loopSeedLedgers.totalReserved(entry.what()));
            long protectedAmount = addSaturated(
                    reusableReserve, retainedFinalOutputs.get(entry.what()));
            long deliverable = Math.max(0L, held - protectedAmount);
            long offer = Math.min(
                    entry.amount(), Math.min(deliverable, activeJob.remainingAmount));
            if (offer <= 0) continue;

            long acceptable = offerToRequester(
                    activeJob, entry.what(), offer, Actionable.SIMULATE);
            if (acceptable <= 0) continue;
            if (job != activeJob || activeJob.link.isCanceled()) return;

            long removed = inventory.extract(
                    entry.what(), acceptable, Actionable.MODULATE);
            if (removed <= 0) continue;

            long delivered = offerToRequester(
                    activeJob, entry.what(), removed, Actionable.MODULATE);
            if (delivered < removed) {
                inventory.insert(entry.what(), removed - delivered, Actionable.MODULATE);
            }
            if (job != activeJob) return;
            if (delivered > 0) {
                removeUpTo(pendingRequesterOutputs, entry.what(), delivered);
                finishCompletedFinalOutput(activeJob, entry.what(), delivered);
                cpu.markDirty();
            }
        }

        if (!pendingRequesterOutputs.isEmpty()) {
            cantStoreItems = true;
        }
    }

    private void capPendingRequesterOutputsToRemaining(
            long remainingDemand, @Nullable AEKey newlyCompletedKey) {
        if (pendingRequesterOutputs.isEmpty()) return;

        var pending = new ArrayList<PendingRequesterOutputAccounting.Credit<AEKey>>();
        for (var entry : pendingRequesterOutputs) {
            pending.add(new PendingRequesterOutputAccounting.Credit<>(
                    entry.getKey(), entry.getLongValue()));
        }
        var reconciliation = PendingRequesterOutputAccounting.capToRemainingDemand(
                pending, remainingDemand, newlyCompletedKey);
        pendingRequesterOutputs.clear();
        for (var retained : reconciliation.retained()) {
            pendingRequesterOutputs.add(retained.key(), retained.amount());
        }
    }

    private static long offerToRequester(
            TimeWheelJob activeJob, AEKey what, long amount, Actionable type) {
        if (activeJob == null || what == null || amount <= 0) return 0L;
        long accepted = activeJob.link.insert(what, amount, type);
        return Math.min(amount, Math.max(0L, accepted));
    }

    private void flushUnusedRetainedFinalOutputs(TimeWheelJob activeJob) {
        if (activeJob == null || retainedFinalOutputs.isEmpty()) return;
        var retained = new ArrayList<GenericStack>();
        for (var entry : retainedFinalOutputs) {
            if (entry.getLongValue() > 0 && pendingLoopSeedDemand(activeJob, entry.getKey()) <= 0) {
                retained.add(new GenericStack(entry.getKey(), entry.getLongValue()));
            }
        }
        for (var entry : retained) {
            long held = inventory.extract(entry.what(), Long.MAX_VALUE, Actionable.SIMULATE);
            long free = Math.max(0L, held - loopSeedLedgers.totalReserved(entry.what()));
            long offer = Math.min(entry.amount(), Math.min(free, activeJob.remainingAmount));
            if (offer <= 0) continue;
            long accepted = offerToRequester(
                    activeJob, entry.what(), offer, Actionable.SIMULATE);
            if (accepted <= 0) continue;
            if (job != activeJob || activeJob.link.isCanceled()) return;
            long removed = inventory.extract(entry.what(), accepted, Actionable.MODULATE);
            if (removed <= 0) continue;
            long delivered = offerToRequester(
                    activeJob, entry.what(), removed, Actionable.MODULATE);
            if (delivered < removed) {
                inventory.insert(entry.what(), removed - delivered, Actionable.MODULATE);
            }
            if (job != activeJob) return;
            if (delivered > 0) {
                removeUpTo(retainedFinalOutputs, entry.what(), delivered);
                finishCompletedFinalOutput(activeJob, entry.what(), delivered);
                cpu.markDirty();
            }
        }
    }

    /**
     * Recovers an exact final-output stack that has lost its transient ownership record after all
     * dispatched work has returned. This can happen when an overload output re-enters the same ME
     * storage while its requester callback is guarded against recursion: the physical stack is
     * retained by the CPU, but no task, waiting entry, or overload entry remains to retry it.
     *
     * <p>The recovery is deliberately terminal-only and excludes every reusable-seed and retained
     * output reservation. It therefore cannot consume an input that a still-running pattern needs.
     */
    private void recoverTerminalFinalOutputFromInventory(TimeWheelJob activeJob) {
        if (activeJob == null
                || activeJob.softCancelling
                || activeJob.remainingAmount <= 0
                || activeJob.finalOutput == null
                || !activeJob.tasks.isEmpty()
                || !activeJob.waitingKeys.isEmpty()
                || !activeJob.pendingOutputs.isEmpty()
                || !pendingRequesterOutputs.isEmpty()
                || OverloadCpuStateManager.INSTANCE.hasAnyPending(this)) {
            return;
        }

        var finalKey = activeJob.finalOutput.what();
        long held = inventory.extract(finalKey, Long.MAX_VALUE, Actionable.SIMULATE);
        long reusableReserve = Math.max(
                seedReturnQuota.get(finalKey), loopSeedLedgers.totalReserved(finalKey));
        long recoverable = FinalOutputProgress.recoverableInventoryAmount(
                held, reusableReserve, retainedFinalOutputs.get(finalKey),
                activeJob.remainingAmount);
        if (recoverable <= 0) return;

        if (activeJob.link.isStandalone()) {
            finishCompletedFinalOutput(activeJob, finalKey, recoverable);
            cpu.markDirty();
            return;
        }

        long acceptable = offerToRequester(
                activeJob, finalKey, recoverable, Actionable.SIMULATE);
        if (acceptable <= 0 || job != activeJob || activeJob.link.isCanceled()) return;

        long removed = inventory.extract(finalKey, acceptable, Actionable.MODULATE);
        if (removed <= 0) return;

        long delivered = offerToRequester(
                activeJob, finalKey, removed, Actionable.MODULATE);
        if (delivered < removed) {
            inventory.insert(finalKey, removed - delivered, Actionable.MODULATE);
        }
        if (job != activeJob) return;
        if (delivered > 0) {
            finishCompletedFinalOutput(activeJob, finalKey, delivered);
            AELog.warn(
                    "[ae2lt] Recovered %d terminal final-output units from CPU inventory for crafting job %s.",
                    delivered, activeJob.link.getCraftingID());
            cpu.markDirty();
        }
    }

    private static void removeUpTo(KeyCounter counter, AEKey key, long amount) {
        if (counter == null || key == null || amount <= 0) return;
        long current = Math.max(0L, counter.get(key));
        if (current <= 0) {
            // KeyCounter keeps zero-valued records unless callers remove them explicitly.
            counter.remove(key);
            return;
        }
        long remaining = Math.max(0L, current - Math.max(0L, amount));
        if (remaining == current) return;
        if (remaining <= 0) {
            counter.remove(key);
        } else {
            counter.set(key, remaining);
        }
    }

    private void clearLoopSeedState() {
        loopSeedLedgers.clear();
    }

    private void readLoopSeedState(CompoundTag data, HolderLookup.Provider registries) {
        loopSeedLedgers.readFromNBT(data, registries);
    }

    private void writeLoopSeedState(CompoundTag data, HolderLookup.Provider registries) {
        loopSeedLedgers.writeToNBT(data, registries);
    }

    private static KeyCounter readCounter(
            ListTag tags, HolderLookup.Provider registries) {
        var result = new KeyCounter();
        for (int i = 0; i < tags.size(); i++) {
            var stack = GenericStack.readTag(registries, tags.getCompound(i));
            if (stack != null && stack.amount() > 0) result.add(stack.what(), stack.amount());
        }
        return result;
    }

    private static ListTag writeCounter(
            KeyCounter counter, HolderLookup.Provider registries) {
        var result = new ListTag();
        for (var entry : counter) {
            if (entry.getLongValue() > 0) {
                result.add(GenericStack.writeTag(
                        registries, new GenericStack(entry.getKey(), entry.getLongValue())));
            }
        }
        return result;
    }

    private static Map<UUID, KeyCounter> readSeedCredits(
            ListTag tags, HolderLookup.Provider registries) {
        var result = new LinkedHashMap<UUID, KeyCounter>();
        for (int i = 0; i < tags.size(); i++) {
            var creditTag = tags.getCompound(i);
            if (!creditTag.hasUUID(NBT_CREDIT_CONSUMER)) continue;
            var items = readCounter(
                    creditTag.getList(NBT_CREDIT_ITEMS, Tag.TAG_COMPOUND), registries);
            if (!items.isEmpty()) {
                result.put(creditTag.getUUID(NBT_CREDIT_CONSUMER), items);
            }
        }
        return Map.copyOf(result);
    }

    private static ListTag writeSeedCredits(
            Map<UUID, KeyCounter> credits, HolderLookup.Provider registries) {
        var result = new ListTag();
        var consumers = new ArrayList<>(credits.keySet());
        consumers.sort(UUID::compareTo);
        for (var consumer : consumers) {
            var items = credits.get(consumer);
            if (items == null || items.isEmpty()) continue;
            var creditTag = new CompoundTag();
            creditTag.putUUID(NBT_CREDIT_CONSUMER, consumer);
            creditTag.put(NBT_CREDIT_ITEMS, writeCounter(items, registries));
            result.add(creditTag);
        }
        return result;
    }

    private static KeyCounter copyCounter(KeyCounter source) {
        var copy = new KeyCounter();
        copy.addAll(source);
        return copy;
    }

    private void rollbackHostSeeds(KeyCounter hostSeeds) {
        for (var entry : hostSeeds) {
            long removed = inventory.extract(entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
            if (removed > 0) {
                long returned = cpu.getHost().insertReusableSeed(
                        entry.getKey(), removed, Actionable.MODULATE);
                if (returned < removed) {
                    inventory.insert(
                            entry.getKey(), removed - Math.max(0L, returned), Actionable.MODULATE);
                }
            }
        }
    }

    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        this.inventory.readFromNBT(data.getList(TAG_INVENTORY, Tag.TAG_COMPOUND), registries);
        this.job = null;
        this.pendingJobTag = null;
        this.pendingOverloadTag = null;
        OverloadCpuStateManager.INSTANCE.clear(this);
        seedReturnQuota.clear();
        retainedFinalOutputs.clear();
        pendingRequesterOutputs.clear();
        seedReturnQuotaFinalized = data.getBoolean(TAG_SEED_RETURN_QUOTA_FINALIZED);
        clearLoopSeedState();
        if (data.contains(TAG_SEED_RETURN_QUOTA, Tag.TAG_LIST)) {
            var seeds = data.getList(TAG_SEED_RETURN_QUOTA, Tag.TAG_COMPOUND);
            for (int i = 0; i < seeds.size(); i++) {
                var stack = GenericStack.readTag(registries, seeds.getCompound(i));
                if (stack != null && stack.amount() > 0) seedReturnQuota.add(stack.what(), stack.amount());
            }
        }
        if (data.contains(TAG_RETAINED_FINAL_OUTPUTS, Tag.TAG_LIST)) {
            var retained = data.getList(TAG_RETAINED_FINAL_OUTPUTS, Tag.TAG_COMPOUND);
            for (int i = 0; i < retained.size(); i++) {
                var stack = GenericStack.readTag(registries, retained.getCompound(i));
                if (stack != null && stack.amount() > 0) {
                    retainedFinalOutputs.add(stack.what(), stack.amount());
                }
            }
        }
        if (data.contains(TAG_PENDING_REQUESTER_OUTPUTS, Tag.TAG_LIST)) {
            pendingRequesterOutputs.addAll(readCounter(
                    data.getList(TAG_PENDING_REQUESTER_OUTPUTS, Tag.TAG_COMPOUND), registries));
        }
        readLoopSeedState(data, registries);
        clearTaskWheel();

        if (data.contains(TAG_JOB, Tag.TAG_COMPOUND)) {
            var jobTag = data.getCompound(TAG_JOB);
            var overloadTag = data.contains(TAG_OVERLOAD_STATE, Tag.TAG_COMPOUND)
                    ? data.getCompound(TAG_OVERLOAD_STATE)
                    : null;
            if (cpu.getLevel() == null) {
                this.pendingJobTag = jobTag.copy();
                this.pendingOverloadTag = overloadTag != null ? overloadTag.copy() : null;
                updatePendingDisplayedOutput(jobTag, registries);
            } else {
                restoreJobFromNBT(jobTag, overloadTag, registries);
            }
        } else {
            cpu.updateOutput(null);
        }
    }

    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        if (!this.inventory.list.isEmpty()) {
            data.put(TAG_INVENTORY, this.inventory.writeToNBT(registries));
        } else {
            data.remove(TAG_INVENTORY);
        }
        if (!seedReturnQuota.isEmpty()) {
            var seeds = new ListTag();
            for (var entry : seedReturnQuota) {
                seeds.add(GenericStack.writeTag(registries,
                        new GenericStack(entry.getKey(), entry.getLongValue())));
            }
            data.put(TAG_SEED_RETURN_QUOTA, seeds);
        } else {
            data.remove(TAG_SEED_RETURN_QUOTA);
        }
        if (seedReturnQuotaFinalized) {
            data.putBoolean(TAG_SEED_RETURN_QUOTA_FINALIZED, true);
        } else {
            data.remove(TAG_SEED_RETURN_QUOTA_FINALIZED);
        }
        if (!retainedFinalOutputs.isEmpty()) {
            data.put(TAG_RETAINED_FINAL_OUTPUTS,
                    writeCounter(retainedFinalOutputs, registries));
        } else {
            data.remove(TAG_RETAINED_FINAL_OUTPUTS);
        }
        if (!pendingRequesterOutputs.isEmpty()) {
            data.put(TAG_PENDING_REQUESTER_OUTPUTS,
                    writeCounter(pendingRequesterOutputs, registries));
        } else {
            data.remove(TAG_PENDING_REQUESTER_OUTPUTS);
        }
        writeLoopSeedState(data, registries);
        if (this.job != null) {
            data.put(TAG_JOB, this.job.writeToNBT(registries));
            var overloadTag = OverloadCpuStateManager.INSTANCE.writeToTag(this, registries);
            if (overloadTag != null) {
                data.put(TAG_OVERLOAD_STATE, overloadTag);
            } else {
                data.remove(TAG_OVERLOAD_STATE);
            }
        } else if (this.pendingJobTag != null) {
            data.put(TAG_JOB, this.pendingJobTag.copy());
            if (this.pendingOverloadTag != null) {
                data.put(TAG_OVERLOAD_STATE, this.pendingOverloadTag.copy());
            } else {
                data.remove(TAG_OVERLOAD_STATE);
            }
        } else {
            data.remove(TAG_JOB);
            data.remove(TAG_OVERLOAD_STATE);
        }
    }

    public void resolvePendingLoad() {
        if (this.pendingJobTag == null) {
            return;
        }

        var level = cpu.getLevel();
        if (level == null) {
            return;
        }

        var jobTag = this.pendingJobTag;
        var overloadTag = this.pendingOverloadTag;
        this.pendingJobTag = null;
        this.pendingOverloadTag = null;
        restoreJobFromNBT(jobTag, overloadTag, level.registryAccess());
    }

    private void restoreJobFromNBT(CompoundTag jobTag,
                                   @Nullable CompoundTag overloadTag,
                                   HolderLookup.Provider registries) {
        this.job = readJobFromNBT(jobTag, registries);
        patternPowerCache.clear();
        if (this.job == null || this.job.finalOutput == null) {
            cpu.updateOutput(null);
            finishJob(false);
            return;
        }
        loopSeedLedgers.registerConsumers(this.job.loopPatterns());

        cpu.updateOutput(new GenericStack(job.finalOutput.what(), job.remainingAmount));
        markWaitingKeysChanged();
        if (overloadTag != null) {
            OverloadCpuStateManager.INSTANCE.readFromTag(
                    this,
                    job.link.getCraftingID(),
                    overloadTag,
                    registries);
        }
        rebuildTaskWheel();
    }

    private void updatePendingDisplayedOutput(CompoundTag jobTag, HolderLookup.Provider registries) {
        var finalOutput = GenericStack.readTag(registries, jobTag.getCompound(NBT_FINAL_OUTPUT));
        if (finalOutput == null) {
            cpu.updateOutput(null);
            return;
        }

        var remainingAmount = jobTag.getLong(NBT_REMAINING_AMOUNT);
        cpu.updateOutput(remainingAmount > 0 ? new GenericStack(finalOutput.what(), remainingAmount) : null);
    }

    @Nullable
    private TimeWheelJob readJobFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        var loadedJob = TimeWheelJob.readFromNBT(data, registries, this::postChange, cpu);
        if (loadedJob == null) {
            return null;
        }

        var grid = cpu.getGrid();
        if (grid != null) {
            ((CraftingService) grid.getCraftingService()).addLink(loadedJob.link);
        }
        return loadedJob;
    }

    @Nullable
    public ICraftingLink getLastLink() {
        return this.job != null ? this.job.link : null;
    }

    public ListCraftingInventory getInventory() {
        return inventory;
    }

    public long getLastModifiedOnTick() {
        return lastModifiedOnTick;
    }

    public long getWaitingKeysModifiedOnTick() {
        return waitingKeysModifiedOnTick;
    }

    public boolean hasJob() {
        return this.job != null || this.pendingJobTag != null;
    }

    public boolean hasPersistentState() {
        return this.job != null
                || this.pendingJobTag != null
                || this.pendingOverloadTag != null
                || !this.inventory.list.isEmpty()
                || !seedReturnQuota.isEmpty()
                || !pendingRequesterOutputs.isEmpty()
                || OverloadCpuStateManager.INSTANCE.hasAnyPending(this);
    }

    @Nullable
    public GenericStack getFinalJobOutput() {
        return this.job != null ? this.job.finalOutput : null;
    }

    public ElapsedTimeTracker getElapsedTimeTracker() {
        return this.job != null ? this.job.timeTracker : new ElapsedTimeTracker();
    }

    public void addListener(Consumer<AEKey> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<AEKey> listener) {
        listeners.remove(listener);
    }

    public long getStored(AEKey template) {
        return this.inventory.extract(template, Long.MAX_VALUE, Actionable.SIMULATE);
    }

    public long getWaitingFor(AEKey template) {
        return this.job != null
                ? this.job.waitingFor.extract(template, Long.MAX_VALUE, Actionable.SIMULATE)
                : 0L;
    }

    public void getAllWaitingFor(Set<AEKey> waitingFor) {
        if (this.job == null) {
            return;
        }
        waitingFor.addAll(this.job.waitingKeys);
    }

    public long getPendingOutputs(AEKey template) {
        return this.job != null ? this.job.pendingOutputs.get(template) : 0L;
    }

    public void getAllItems(KeyCounter out) {
        out.addAll(this.inventory.list);
        if (this.job == null) {
            return;
        }

        out.addAll(job.waitingFor.list);
        out.addAll(job.pendingOutputs);
    }

    public boolean isCantStoreItems() {
        return cantStoreItems;
    }

    public boolean isJobSuspended() {
        return job != null && job.suspended;
    }

    public boolean isSoftCancelling() {
        return job != null && job.softCancelling;
    }

    public void setJobSuspended(boolean suspended) {
        if (job != null && job.suspended != suspended) {
            job.suspended = suspended;
            cpu.markDirty();
        }
    }

    private Iterable<ResolvedProvider> providersForSinglePush(CraftingService craftingService,
                                                              IPatternDetails details,
                                                              TickProviderDispatchSchedule dispatchSchedule) {
        var providerPattern = CraftingPatternDelegates.forProviderLookup(details);
        var skipped = batchedByTask.get(details);
        return () -> new Iterator<>() {
            private final Iterator<ICraftingProvider> raw = dispatchSchedule
                    .candidates(craftingService, providerPattern, providerPattern)
                    .iterator();
            @Nullable
            private ResolvedProvider next;

            @Override
            public boolean hasNext() {
                while (next == null && raw.hasNext()) {
                    var candidate = raw.next();
                    if (skipped == null || !skipped.containsKey(candidate)) {
                        next = new ResolvedProvider(candidate, providerPattern);
                    }
                }
                return next != null;
            }

            @Override
            public ResolvedProvider next() {
                if (!hasNext()) {
                    throw new java.util.NoSuchElementException();
                }
                var result = next;
                next = null;
                return result;
            }
        };
    }

    private record ResolvedProvider(ICraftingProvider provider, IPatternDetails pattern) { }

    private boolean hasAmbiguousOverloadOutput(IPatternDetails details) {
        return hasAmbiguousOverloadOutput(details, null);
    }

    private boolean hasAmbiguousOverloadOutput(
            IPatternDetails details,
            @Nullable Map<UUID, KeyCounter> preallocatedRemainderCredits) {
        var providerDetails = CraftingPatternDelegates.forProviderLookup(details);
        if (!(providerDetails instanceof OverloadedProviderOnlyPatternDetails overloadDetails)) {
            // STRICT outputs and remainders are partitioned from mirrored exact ID_ONLY waiting
            // in insert(). They no longer need to serialize behind unrelated overload work.
            return false;
        }

        var reference = overloadPatternReference(details, overloadDetails);
        if (details instanceof ExecuteLoopPattern && preallocatedRemainderCredits != null) {
            var seedMetadata = reusableSeedOverloadOutputs(
                    details, overloadDetails, 1L, preallocatedRemainderCredits);
            var outputs = details.getOutputs();
            for (var entry : seedMetadata.entrySet()) {
                if (entry.getKey() < 0 || entry.getKey() >= outputs.size()) return true;
                var planned = outputs.get(entry.getKey()).what();
                for (var credit : entry.getValue().consumerCredits()) {
                    if (!loopSeedLedgers.acceptsLateBoundVariantCredit(
                            credit.consumerId(), planned)) return true;
                }
            }
        }
        if (OverloadCpuStateManager.INSTANCE.hasAmbiguousOutputRegistration(
                this,
                reference,
                overloadDetails.overloadPatternDetailsView())) {
            return true;
        }
        return false;
    }

    private void recordPushedPattern(TimeWheelJob activeJob,
                                     IPatternDetails details,
                                     KeyCounter expectedOutputs,
                                     KeyCounter expectedContainerItems,
                                     long copies,
                                     Map<UUID, KeyCounter> remainderLoopCredits) {
        for (var expectedOutput : expectedOutputs) {
            insertWaitingFor(
                    activeJob,
                    expectedOutput.getKey(),
                    multiplySaturated(expectedOutput.getLongValue(), copies));
        }
        for (var expectedContainerItem : expectedContainerItems) {
            long amount = multiplySaturated(expectedContainerItem.getLongValue(), copies);
            insertWaitingFor(activeJob, expectedContainerItem.getKey(), amount);
            addMaxItems(activeJob.timeTracker, amount, expectedContainerItem.getKey().getType());
        }

        registerOverloadExpectedOutputs(activeJob, details, copies, remainderLoopCredits);
        cpu.markDirty();
    }

    private void registerOverloadExpectedOutputs(TimeWheelJob activeJob,
                                                 IPatternDetails details,
                                                 long copies,
                                                 Map<UUID, KeyCounter> remainderLoopCredits) {
        if (!(CraftingPatternDelegates.forProviderLookup(details)
                instanceof OverloadedProviderOnlyPatternDetails overloadDetails) || copies <= 0) {
            return;
        }

        var reference = overloadPatternReference(details, overloadDetails);
        var finalOutputKey = activeJob.finalOutput != null ? activeJob.finalOutput.what() : null;
        OverloadCpuStateManager.INSTANCE.registerExpectedOutputs(
                this,
                activeJob.link.getCraftingID(),
                reference,
                overloadDetails.overloadPatternDetailsView(),
                details.getOutputs(),
                finalOutputKey,
                copies,
                reusableSeedOverloadOutputs(
                        details, overloadDetails, copies, remainderLoopCredits));
    }

    private OverloadPatternReference overloadPatternReference(
            IPatternDetails details,
            OverloadedProviderOnlyPatternDetails overloadDetails) {
        var identity = overloadDetails.overloadPatternIdentity();
        if (details instanceof ExecuteLoopPattern loop) {
            identity += "#loop-seed:" + loop.reusableSeedGroupId();
        }
        return new OverloadPatternReference(
                identity, overloadDetails.overloadPatternDetailsView().sourcePattern());
    }

    private Map<Integer, OverloadReusableSeedMetadata> reusableSeedOverloadOutputs(
            IPatternDetails details,
            OverloadedProviderOnlyPatternDetails overloadDetails,
            long copies,
            Map<UUID, KeyCounter> remainderLoopCredits) {
        if (!(details instanceof ExecuteLoopPattern loop) || copies <= 0) return Map.of();
        // Keep the ownership split all the way through the fuzzy-output pending queue. The
        // producer is irrelevant after registration: each returned unit is re-keyed directly in
        // the fixed downstream consumer account that was credited when this pattern dispatched.
        var remainingCredits = new LinkedHashMap<AEKey, LinkedHashMap<UUID, Long>>();
        for (var target : loop.runtimeOutputSeedCredits().entrySet()) {
            for (var output : target.getValue()) {
                long amount = Sat.mul(output.getLongValue(), copies);
                if (amount <= 0) continue;
                remainingCredits
                        .computeIfAbsent(output.getKey(), ignored -> new LinkedHashMap<>())
                        .merge(target.getKey(), amount, Ae2LtTimeWheelCraftingCpuLogic::addSaturated);
            }
        }
        if (remainingCredits.isEmpty()) return Map.of();
        consumePreallocatedLoopCredits(remainingCredits, remainderLoopCredits);
        var actualOutputs = details.getOutputs();
        // When strict and ID_ONLY slots expose the same planned key, consume the strict physical
        // capacity first. Otherwise a strict downstream consumer could be assigned the fuzzy slot
        // merely because overload metadata is registered separately from AE2's waiting list.
        for (var output : overloadDetails.overloadPatternDetailsView().outputs()) {
            if (output.matchMode() == MatchMode.ID_ONLY) continue;
            int slot = output.slotIndex();
            if (slot < 0 || slot >= actualOutputs.size()) continue;
            var byConsumer = remainingCredits.get(actualOutputs.get(slot).what());
            if (byConsumer != null && !byConsumer.isEmpty()) {
                takeConsumerCredits(
                        byConsumer,
                        multiplySaturated(output.amountPerCraft(), copies));
            }
        }
        var result = new HashMap<Integer, OverloadReusableSeedMetadata>();
        for (var output : overloadDetails.overloadPatternDetailsView().outputs()) {
            if (output.matchMode() != MatchMode.ID_ONLY) continue;
            int slot = output.slotIndex();
            if (slot < 0 || slot >= actualOutputs.size()) continue;
            var expected = actualOutputs.get(slot).what();
            var byConsumer = remainingCredits.get(expected);
            if (byConsumer == null || byConsumer.isEmpty()) continue;
            long slotAmount = multiplySaturated(output.amountPerCraft(), copies);
            var credits = takeConsumerCredits(byConsumer, slotAmount);
            if (credits.isEmpty()) continue;
            result.put(slot, new OverloadReusableSeedMetadata(
                    credits, loop.hasSingleSeedInputPerMember()));
        }
        return result.isEmpty() ? Map.of() : Map.copyOf(result);
    }

    private static void consumePreallocatedLoopCredits(
            LinkedHashMap<AEKey, LinkedHashMap<UUID, Long>> remainingCredits,
            Map<UUID, KeyCounter> preallocatedLoopCredits) {
        if (preallocatedLoopCredits == null || preallocatedLoopCredits.isEmpty()) return;
        for (var consumer : preallocatedLoopCredits.entrySet()) {
            for (var credit : consumer.getValue()) {
                var byConsumer = remainingCredits.get(credit.getKey());
                if (byConsumer == null) {
                    throw new IllegalStateException(
                            "preallocated loop credit has no matching planned output");
                }
                long available = byConsumer.getOrDefault(consumer.getKey(), 0L);
                if (credit.getLongValue() > available) {
                    throw new IllegalStateException(
                            "preallocated loop credit exceeds its fixed consumer allocation");
                }
                long left = available - credit.getLongValue();
                if (left > 0) byConsumer.put(consumer.getKey(), left);
                else byConsumer.remove(consumer.getKey());
                if (byConsumer.isEmpty()) remainingCredits.remove(credit.getKey());
            }
        }
    }

    private static List<OverloadConsumerCredit> takeConsumerCredits(
            LinkedHashMap<UUID, Long> remainingByConsumer, long maximumAmount) {
        if (remainingByConsumer == null || maximumAmount <= 0) return List.of();
        long remaining = maximumAmount;
        var result = new ArrayList<OverloadConsumerCredit>();
        var iterator = remainingByConsumer.entrySet().iterator();
        while (iterator.hasNext() && remaining > 0) {
            var entry = iterator.next();
            long amount = Math.min(Math.max(0L, entry.getValue()), remaining);
            if (amount > 0) {
                result.add(new OverloadConsumerCredit(entry.getKey(), amount));
                remaining -= amount;
            }
            long left = entry.getValue() - amount;
            if (left <= 0) iterator.remove();
            else entry.setValue(left);
        }
        return List.copyOf(result);
    }

    private void rekeyOverloadReusableSeeds(AEKey incoming, OverloadClaimResult claims) {
        for (var claim : claims.claims()) {
            for (var credit : claim.consumerCredits()) {
                loopSeedLedgers.rekeyAvailable(
                        credit.consumerId(),
                        claim.exactExpectedKey(), incoming, credit.amount());
            }
        }
    }

    private long applyInventoryClaims(TimeWheelJob activeJob, AEKey incoming, OverloadClaimResult claims) {
        long claimed = claims.claimedForInventory();
        if (claimed <= 0) {
            return 0;
        }

        decrementItems(activeJob.timeTracker, claimed, incoming.getType());
        inventory.insert(incoming, claimed, Actionable.MODULATE);
        wakeSchedulerForReturnedInput(incoming);
        return claimed;
    }

    private static long overloadPublicInventory(OverloadClaimResult claims) {
        long result = 0L;
        for (var claim : claims.claims()) {
            if (!claim.routesToRequester()) continue;
            long amount = claim.claimedAmount()
                    - claim.reusableSeedAmount()
                    - claim.requesterAmount();
            if (amount > 0) result = addSaturated(result, amount);
        }
        return result;
    }

    private void markRetainedRequesterClaim(AEKey incoming, long retained) {
        if (retained <= 0) return;
        retainedFinalOutputs.add(incoming, retained);
        cpu.markDirty();
    }

    private long applyRequesterClaims(
            TimeWheelJob activeJob, AEKey incoming, OverloadClaimResult claims) {
        long claimed = claims.claimedForRequester();
        if (claimed <= 0) {
            return 0;
        }

        decrementItems(activeJob.timeTracker, claimed, incoming.getType());
        postChange(incoming);

        activeJob.remainingAmount = Math.max(0L, activeJob.remainingAmount - claimed);
        if (activeJob.remainingAmount > 0) {
            cpu.updateOutput(new GenericStack(activeJob.finalOutput.what(), activeJob.remainingAmount));
        } else {
            cpu.updateOutput(null);
        }
        return claimed;
    }

    private void consumeTaskCopies(TimeWheelJob activeJob, IPatternDetails details, long copies) {
        if (copies <= 0) {
            return;
        }

        var task = activeJob.tasks.get(details);
        if (task == null || task.value <= 0) {
            return;
        }

        long consumed = Math.min(task.value, copies);
        task.value -= consumed;
        activeJob.removePendingOutputs(details, consumed);
        postPatternOutputsChange(details);

        if (task.value <= 0) {
            activeJob.tasks.remove(details);
            clearTaskPreference(details);
        }
    }

    private void setTaskValue(TimeWheelJob activeJob, IPatternDetails details, long value) {
        var task = activeJob.tasks.get(details);
        if (task == null) {
            return;
        }

        long normalized = Math.max(0L, value);
        long oldValue = task.value;
        if (normalized == oldValue) {
            return;
        }

        task.value = normalized;
        if (normalized <= 0) {
            clearTaskPreference(details);
        }
        if (normalized < oldValue) {
            activeJob.removePendingOutputs(details, oldValue - normalized);
        } else {
            activeJob.addPendingOutputs(details, normalized - oldValue);
        }
        postPatternOutputsChange(details);
    }

    private void removeTask(TimeWheelJob activeJob, IPatternDetails details) {
        unparkTask(details);
        var removed = activeJob.tasks.remove(details);
        clearTaskPreference(details);
        if (removed != null && removed.value > 0) {
            activeJob.removePendingOutputs(details, removed.value);
        }
    }

    private void deductClaimedWaitingFor(TimeWheelJob activeJob, OverloadClaimResult claims) {
        for (var claim : claims.claims()) {
            extractWaitingFor(activeJob, claim.exactExpectedKey(), claim.claimedAmount());
        }
    }

    private void insertWaitingFor(TimeWheelJob activeJob, AEKey what, long amount) {
        if (activeJob.insertWaitingFor(what, amount)) {
            markWaitingKeysChanged();
        }
    }

    private long extractWaitingFor(TimeWheelJob activeJob, AEKey what, long amount) {
        var result = activeJob.extractWaitingFor(what, amount);
        if (result.removedKey()) {
            markWaitingKeysChanged();
        }
        return result.extracted();
    }

    private void clearWaitingFor(TimeWheelJob activeJob) {
        if (activeJob.clearWaitingFor()) {
            markWaitingKeysChanged();
        }
    }

    private void markWaitingKeysChanged() {
        waitingKeysModifiedOnTick = TickHandler.instance().getCurrentTick();
    }

    private void prepareScheduler(TimeWheelJob activeJob) {
        advanceWheel();
        if (queueRebuildNeeded || needsSchedulerRebuild(activeJob)) {
            rebuildTaskWheel();
        }
    }

    private boolean needsSchedulerRebuild(TimeWheelJob activeJob) {
        return !activeJob.tasks.isEmpty()
                && queuedTasks.isEmpty()
                && tasksParkedByMissingKey.isEmpty()
                && !hasScheduledWheelEntries();
    }

    private boolean hasScheduledWheelEntries() {
        for (var bucket : taskWheel) {
            if (!bucket.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void advanceWheel() {
        long now = TickHandler.instance().getCurrentTick();
        if (schedulerTick == Long.MIN_VALUE) {
            schedulerTick = now;
            return;
        }

        int delta = (int) Math.max(0L, Math.min(WHEEL_SIZE, now - schedulerTick));
        schedulerTick = now;
        if (delta == 0) {
            return;
        }

        int newCursor = (wheelCursor + delta) & WHEEL_MASK;
        // Everything in a bucket the cursor moves off of is due by now, INCLUDING the old cursor
        // bucket itself: wakes and job (re)builds that run outside executeCrafting (e.g. a provider
        // returning outputs during the post-tick sweep) schedule with delay 0 into the bucket that
        // was already polled this tick. Without draining it, such a task would silently sleep a
        // whole wheel revolution (~64 ticks) — and a tick gap (server stall, chunk reload) would
        // likewise strand every skipped bucket's tasks.
        var dest = taskWheel[newCursor];
        for (int i = 0; i < delta; i++) {
            var skipped = taskWheel[(wheelCursor + i) & WHEEL_MASK];
            if (skipped == dest) {
                continue;
            }
            while (!skipped.isEmpty()) {
                dest.addLast(skipped.pollFirst());
            }
        }
        wheelCursor = newCursor;
    }

    private void rebuildTaskWheel() {
        clearTaskWheel();
        var activeJob = this.job;
        if (activeJob == null) {
            return;
        }

        var entries = new ArrayList<>(activeJob.tasks.entrySet());
        entries.sort((left, right) -> CraftingTaskPriorities.compare(left.getKey(), right.getKey()));
        for (var entry : entries) {
            if (entry.getValue().value > 0) {
                scheduleRebuiltTask(activeJob, entry.getKey());
            }
        }
        queueRebuildNeeded = false;
    }

    private void scheduleRebuiltTask(TimeWheelJob activeJob, IPatternDetails details) {
        if (!hasOnlyExactInputs(details)) {
            scheduleTask(details, 0);
            return;
        }

        var missingKeys = findMissingExactInputKeys(details);
        if (missingKeys.isEmpty()) {
            scheduleTask(details, 0);
            return;
        }

        if (parkTaskForMissingInputs(activeJob, details, missingKeys)) {
            rescheduleIfStillPending(activeJob, details, PARKED_TASK_SAFETY_DELAY_TICKS);
        } else {
            scheduleTask(details, 0);
        }
    }

    private boolean hasOnlyExactInputs(IPatternDetails details) {
        for (var input : details.getInputs()) {
            var possibles = input.getPossibleInputs();
            if (possibles.length != 1) {
                return false;
            }
            if (possibles[0].what() == null) {
                return false;
            }
        }
        return true;
    }

    private void clearTaskWheel() {
        for (var bucket : taskWheel) {
            bucket.clear();
        }
        queuedTasks.clear();
        clearParkedTasks();
        preferredTask = null;
        queueRebuildNeeded = true;
    }

    private void scheduleTask(IPatternDetails details, int delayTicks) {
        var activeJob = this.job;
        if (activeJob == null || !activeJob.tasks.containsKey(details)) {
            return;
        }
        if (!queuedTasks.add(details)) {
            return;
        }
        int slot = (wheelCursor + Math.max(0, delayTicks)) & WHEEL_MASK;
        taskWheel[slot].addLast(details);
    }

    private void unscheduleTask(IPatternDetails details) {
        boolean removed = false;
        for (var bucket : taskWheel) {
            for (var iterator = bucket.iterator(); iterator.hasNext(); ) {
                if (iterator.next() == details) {
                    iterator.remove();
                    removed = true;
                }
            }
        }
        if (removed) {
            queuedTasks.remove(details);
        }
    }

    private boolean parkTaskForMissingInputs(TimeWheelJob activeJob, IPatternDetails details) {
        if (!activeJob.tasks.containsKey(details) || !hasOnlyExactInputs(details)) {
            return false;
        }

        return parkTaskForMissingInputs(activeJob, details, findMissingExactInputKeys(details));
    }

    private boolean parkTaskForMissingInputs(TimeWheelJob activeJob, IPatternDetails details, Set<AEKey> missingKeys) {
        if (missingKeys.isEmpty()) {
            return false;
        }

        unparkTask(details);
        var taskKeys = new ArrayList<Object>(missingKeys.size());
        for (var key : missingKeys) {
            taskKeys.add(key != null ? key.getPrimaryKey() : null);
        }
        return tasksParkedByMissingKey.park(details, taskKeys);
    }

    private void unparkTask(IPatternDetails details) {
        tasksParkedByMissingKey.unpark(details);
    }

    private void clearParkedTasks() {
        tasksParkedByMissingKey.clear();
    }

    private Set<AEKey> findMissingExactInputKeys(IPatternDetails details) {
        var exactRequired = new HashMap<AEKey, Long>();

        for (var input : details.getInputs()) {
            long multiplier = input.getMultiplier();
            var possibles = input.getPossibleInputs();
            if (possibles.length != 1) {
                return Set.of();
            }

            var possible = possibles[0];
            var key = possible.what();
            long perCopy = multiplySaturated(possible.amount(), multiplier);
            if (key == null || perCopy <= 0) {
                continue;
            }
            exactRequired.merge(key, perCopy, Ae2LtTimeWheelCraftingCpuLogic::addSaturated);
        }

        var missing = new HashSet<AEKey>();
        for (var entry : exactRequired.entrySet()) {
            long required = entry.getValue();
            if (required > 0 && inventory.extract(entry.getKey(), required, Actionable.SIMULATE) < required) {
                missing.add(entry.getKey());
            }
        }
        return missing;
    }

    @Nullable
    private IPatternDetails pollDueTask(TimeWheelJob activeJob) {
        var bucket = taskWheel[wheelCursor];
        IPatternDetails selected = null;
        for (var iterator = bucket.iterator(); iterator.hasNext(); ) {
            var details = iterator.next();
            var task = activeJob.tasks.get(details);
            if (task == null || task.value <= 0) {
                iterator.remove();
                queuedTasks.remove(details);
                clearTaskPreference(details);
                continue;
            }
            if (selected == null
                    || CraftingTaskPriorities.compare(details, selected, preferredTask) < 0) {
                selected = details;
            }
        }
        if (selected == null) return null;
        for (var iterator = bucket.iterator(); iterator.hasNext(); ) {
            if (iterator.next() == selected) {
                iterator.remove();
                break;
            }
        }
        queuedTasks.remove(selected);
        unparkTask(selected);
        return selected;
    }

    private void rescheduleIfStillPending(TimeWheelJob activeJob, IPatternDetails details, int delayTicks) {
        var task = activeJob.tasks.get(details);
        if (task != null && task.value > 0) {
            scheduleTask(details, delayTicks);
        }
    }

    private void preferTaskWhilePending(TimeWheelJob activeJob, IPatternDetails details) {
        if (preferredTask != null) return;
        // A loop member may be split into several execution tasks with the same member order and
        // seed consumer but different downstream output-credit routes. Pinning the first successful
        // split here lets it consume the whole shared bootstrap seed before its siblings run. The
        // downstream members can then return only one side of the seed cycle and the job deadlocks
        // (large jobs expose this while a one-copy job often does not). Closed-loop tasks already
        // carry an explicit dispatch order, so leave equal-order members in queue order and let the
        // time wheel rotate them fairly. Ordinary AE2 tasks retain task-major preference.
        if (details instanceof ISeedPreservingCraftingTask) return;
        var task = activeJob.tasks.get(details);
        if (task != null && task.value > 0) {
            preferredTask = details;
        }
    }

    private void clearTaskPreference(IPatternDetails details) {
        if (preferredTask == details) {
            preferredTask = null;
        }
    }

    private void rescheduleFailedTask(TimeWheelJob activeJob, IPatternDetails details, DispatchOutcome outcome) {
        if (outcome == DispatchOutcome.RETRY_MISSING_INPUT && parkTaskForMissingInputs(activeJob, details)) {
            rescheduleIfStillPending(activeJob, details, PARKED_TASK_SAFETY_DELAY_TICKS);
            return;
        }

        unparkTask(details);
        rescheduleIfStillPending(activeJob, details, retryDelayTicks(outcome));
    }

    private int retryDelayTicks(DispatchOutcome outcome) {
        return switch (outcome) {
            case RETRY_SOON -> 1;
            case RETRY_MISSING_INPUT, RETRY_NO_POWER, RETRY_LATER -> RETRY_DELAY_TICKS;
            case PUSHED -> 0;
        };
    }

    /**
     * Finishes the physical CPU's scheduling tick.
     *
     * <p>A pooled time-wheel CPU can visit this virtual CPU several times in one server tick:
     * first with a one-dispatch discovery allowance, then with larger productive quanta. Due
     * tasks must remain in the current bucket between those visits, otherwise a successful
     * discovery dispatch incorrectly limits the virtual CPU to one ordinary push per tick.</p>
     */
    void finishPhysicalSchedulingTick() {
        carryOverDueTasks();
    }

    private void carryOverDueTasks() {
        var bucket = taskWheel[wheelCursor];
        if (bucket.isEmpty()) {
            return;
        }

        var nextBucket = taskWheel[(wheelCursor + 1) & WHEEL_MASK];
        while (!bucket.isEmpty()) {
            nextBucket.addLast(bucket.pollFirst());
        }
    }

    private void wakeSchedulerForReturnedInput(AEKey what) {
        if (this.job == null || what == null) {
            return;
        }

        var tasksToWake = tasksParkedByMissingKey.wake(what.getPrimaryKey());
        if (tasksToWake.isEmpty()) {
            return;
        }

        for (var details : tasksToWake) {
            unparkTask(details);
            unscheduleTask(details);
            scheduleTask(details, 0);
        }
    }

    private void postChange(@Nullable AEKey what) {
        if (what == null) {
            return;
        }

        if (batchingStatusChanges) {
            batchedStatusChanges.add(what);
            return;
        }

        lastModifiedOnTick = TickHandler.instance().getCurrentTick();
        for (var listener : listeners) {
            listener.accept(what);
        }
    }

    private void beginStatusChangeBatch() {
        batchingStatusChanges = true;
        batchedStatusChanges.clear();
    }

    private void endStatusChangeBatch() {
        batchingStatusChanges = false;
        if (batchedStatusChanges.isEmpty()) {
            return;
        }

        lastModifiedOnTick = TickHandler.instance().getCurrentTick();
        statusChangeScratch.clear();
        statusChangeScratch.addAll(batchedStatusChanges);
        batchedStatusChanges.clear();
        for (var key : statusChangeScratch) {
            for (var listener : listeners) {
                listener.accept(key);
            }
        }
        statusChangeScratch.clear();
    }

    private void postPatternOutputsChange(IPatternDetails details) {
        for (var output : details.getOutputs()) {
            postChange(output.what());
        }
    }

    private void notifyJobOwner(TimeWheelJob activeJob, CraftingJobStatusPacket.Status status) {
        this.lastModifiedOnTick = TickHandler.instance().getCurrentTick();
        var playerId = activeJob.playerId;
        if (playerId == null || cpu.getLevel() == null || cpu.getLevel().getServer() == null) {
            return;
        }

        var connectedPlayer = IPlayerRegistry.getConnected(cpu.getLevel().getServer(), playerId);
        if (connectedPlayer != null) {
            ClientboundPacket message = new CraftingJobStatusPacket(
                    activeJob.link.getCraftingID(),
                    activeJob.finalOutput.what(),
                    activeJob.finalOutput.amount(),
                    activeJob.remainingAmount,
                    status);
            connectedPlayer.connection.send(message);
        }
    }

    @SuppressWarnings("unchecked")
    private static ArrayDeque<IPatternDetails>[] createWheel() {
        var wheel = new ArrayDeque[WHEEL_SIZE];
        for (int i = 0; i < wheel.length; i++) {
            wheel[i] = new ArrayDeque<IPatternDetails>();
        }
        return wheel;
    }

    private static void addMaxItems(ElapsedTimeTracker tracker, long count, AEKeyType type) {
        ((ElapsedTimeTrackerAccessor) tracker).ae2lt$addMaxItems(count, type);
    }

    private static void decrementItems(ElapsedTimeTracker tracker, long count, AEKeyType type) {
        ((ElapsedTimeTrackerAccessor) tracker).ae2lt$decrementItems(count, type);
    }

    private static void clearScratchCounter(KeyCounter counter) {
        // Only zero the entries; keep the per-type submaps allocated. These counters are reused every
        // dispatch, so removeEmptySubmaps() would just churn allocations for no benefit.
        counter.clear();
    }

    private double patternPowerFor(IPatternDetails details, KeyCounter[] craftingContainer) {
        var cached = patternPowerCache.get(details);
        if (cached != null) {
            return cached;
        }
        double power = CraftingCpuHelper.calculatePatternPower(craftingContainer);
        patternPowerCache.put(details, power);
        return power;
    }

    private static long multiplySaturated(long left, long right) {
        if (left <= 0 || right <= 0) {
            return 0L;
        }
        if (left > Long.MAX_VALUE / right) {
            return Long.MAX_VALUE;
        }
        return left * right;
    }

    private static long addSaturated(long left, long right) {
        if (right <= 0) {
            return left;
        }
        if (left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private enum DispatchOutcome {
        PUSHED,
        RETRY_SOON,
        RETRY_MISSING_INPUT,
        RETRY_NO_POWER,
        RETRY_LATER
    }

    /**
     * Result of one {@link #pushBulkForTask} visit. {@code retryDelayTicks} tells the scheduler
     * when to look at the task again: 0 = re-poll this tick (more copies may still fit), 1 =
     * provider contention, {@link #RETRY_DELAY_TICKS} = out of energy.
     */
    private record BulkPush(int dispatched, int retryDelayTicks) {
    }

    private static final class TaskProgress {
        private long value;
    }

    private record WaitingExtract(long extracted, boolean removedKey) {
    }

    private static final class ReservedCraftingInventory
            implements appeng.crafting.inv.ICraftingInventory {
        private final appeng.crafting.inv.ICraftingInventory delegate;
        private final Map<AEKey, Long> reserved;

        private ReservedCraftingInventory(
                appeng.crafting.inv.ICraftingInventory delegate, Map<AEKey, Long> reserved) {
            this.delegate = delegate;
            this.reserved = reserved;
        }

        @Override
        public void insert(AEKey what, long amount, Actionable mode) {
            delegate.insert(what, amount, mode);
        }

        @Override
        public long extract(AEKey what, long amount, Actionable mode) {
            long held = delegate.extract(what, Long.MAX_VALUE, Actionable.SIMULATE);
            long available = Math.max(0L, held - reserved.getOrDefault(what, 0L));
            long requested = Math.min(Math.max(0L, amount), available);
            return requested > 0 ? delegate.extract(what, requested, mode) : 0L;
        }

        @Override
        public Iterable<AEKey> findFuzzyTemplates(AEKey input) {
            return delegate.findFuzzyTemplates(input);
        }
    }

    private static final class TimeWheelJob {
        private final CraftingLink link;
        private final ListCraftingInventory waitingFor;
        private final Set<AEKey> waitingKeys = new HashSet<>();
        private final KeyCounter pendingOutputs = new KeyCounter();
        private final Map<IPatternDetails, TaskProgress> tasks = new HashMap<>();
        private final ElapsedTimeTracker timeTracker;
        private GenericStack finalOutput;
        private long remainingAmount;
        @Nullable
        private Integer playerId;
        private boolean suspended;
        private boolean softCancelling;
        private boolean closedLoopJob;

        private TimeWheelJob(ICraftingPlan plan,
                             Consumer<AEKey> postCraftingDifference,
                             CraftingLink link,
                             @Nullable Integer playerId) {
            this(plan, postCraftingDifference, link, playerId, new ElapsedTimeTracker());
        }

        private TimeWheelJob(ICraftingPlan plan,
                             Consumer<AEKey> postCraftingDifference,
                             CraftingLink link,
                             @Nullable Integer playerId,
                             ElapsedTimeTracker timeTracker) {
            this.finalOutput = plan.finalOutput();
            this.remainingAmount = this.finalOutput.amount();
            this.waitingFor = new ListCraftingInventory(postCraftingDifference::accept);
            this.timeTracker = timeTracker;

            for (var entry : plan.emittedItems()) {
                insertWaitingFor(entry.getKey(), entry.getLongValue());
                addMaxItems(timeTracker, entry.getLongValue(), entry.getKey().getType());
            }
            for (var entry : plan.patternTimes().entrySet()) {
                if (entry.getKey() instanceof ReusableSeedPattern) closedLoopJob = true;
                var expanded = entry.getKey() instanceof PatternFiringExpander expander
                        ? expander.expandPatternFirings(entry.getValue())
                        : Map.of(entry.getKey(), entry.getValue());
                for (var concrete : expanded.entrySet()) {
                    var task = tasks.computeIfAbsent(
                            concrete.getKey(), ignored -> new TaskProgress());
                    task.value = com.moakiee.thunderbolt.core.crafting.planner.Sat.add(
                            task.value, concrete.getValue());
                    addPendingOutputs(concrete.getKey(), concrete.getValue());
                    for (var output : concrete.getKey().getOutputs()) {
                    var amount = multiplySaturated(
                            multiplySaturated(output.amount(), concrete.getValue()),
                            output.what().getAmountPerUnit());
                    addMaxItems(timeTracker, amount, output.what().getType());
                    }
                }
            }
            this.link = link;
            this.playerId = playerId;
        }

        @Nullable
        private static TimeWheelJob readFromNBT(CompoundTag data,
                                                HolderLookup.Provider registries,
                                                Consumer<AEKey> postCraftingDifference,
                                                TimeWheelCraftingCPU cpu) {
            var finalOutput = GenericStack.readTag(registries, data.getCompound(NBT_FINAL_OUTPUT));
            if (finalOutput == null) {
                return null;
            }

            var link = new CraftingLink(data.getCompound(NBT_LINK), cpu);
            var emptyPlan = new TimeWheelPlan(finalOutput);
            var job = new TimeWheelJob(
                    emptyPlan,
                    postCraftingDifference,
                    link,
                    data.contains(NBT_PLAYER_ID, Tag.TAG_INT) ? data.getInt(NBT_PLAYER_ID) : null,
                    new ElapsedTimeTracker(data.getCompound(NBT_TIME_TRACKER)));
            job.remainingAmount = data.getLong(NBT_REMAINING_AMOUNT);
            job.suspended = data.getBoolean(NBT_SUSPENDED);
            job.softCancelling = data.getBoolean(NBT_SOFT_CANCELLING);
            job.closedLoopJob = data.getBoolean(NBT_CLOSED_LOOP_JOB) || job.softCancelling;
            job.waitingFor.readFromNBT(data.getList(NBT_WAITING_FOR, Tag.TAG_COMPOUND), registries);
            job.rebuildWaitingKeys();
            job.tasks.clear();
            job.pendingOutputs.clear();
            job.readTasks(data.getList(NBT_TASKS, Tag.TAG_COMPOUND), registries, cpu.getLevel());
            job.rebuildPendingOutputs();
            if (!job.closedLoopJob) {
                // Saves from before the closedLoopJob tag lack the flag for running loop jobs;
                // loop tasks only ever come from a closed-loop macro, so infer it from them.
                for (var details : job.tasks.keySet()) {
                    if (details instanceof ExecuteLoopPattern) {
                        job.closedLoopJob = true;
                        break;
                    }
                }
            }
            return job;
        }

        private void readTasks(ListTag tasksTag, HolderLookup.Provider registries, @Nullable Level level) {
            if (level == null) {
                return;
            }

            for (int i = 0; i < tasksTag.size(); i++) {
                var item = tasksTag.getCompound(i);
                var pattern = AEItemKey.fromTag(registries, item);
                if (pattern == null) {
                    continue;
                }
                IPatternDetails details = PatternDetailsHelper.decodePattern(pattern, level);
                var remaining = item.getLong(NBT_CRAFTING_PROGRESS);
                if (details != null && remaining > 0) {
                    var inputSeed = readCounter(
                            item.getList(NBT_INPUT_SEED, Tag.TAG_COMPOUND), registries);
                    var initialSeed = readCounter(
                            item.getList(NBT_INITIAL_SEED, Tag.TAG_COMPOUND), registries);
                    var outputSeed = readCounter(
                            item.getList(NBT_OUTPUT_SEED, Tag.TAG_COMPOUND), registries);
                    var consumerId = item.hasUUID(NBT_SEED_CONSUMER)
                            ? item.getUUID(NBT_SEED_CONSUMER) : null;
                    var outputCredits = readSeedCredits(
                            item.getList(NBT_OUTPUT_SEED_CREDITS, Tag.TAG_COMPOUND), registries);
                    var sharedOutputCredits = readSeedCredits(
                            item.getList(
                                    NBT_SHARED_OUTPUT_SEED_CREDITS, Tag.TAG_COMPOUND),
                            registries);
                    boolean hasRoutedCreditTags = item.contains(
                            NBT_OUTPUT_SEED_CREDITS, Tag.TAG_LIST)
                            || item.contains(NBT_SHARED_OUTPUT_SEED_CREDITS, Tag.TAG_LIST);
                    if (consumerId == null && details instanceof ISeedPreservingCraftingTask seeded) {
                        consumerId = seeded.reusableSeedGroupId();
                    }
                    if (!hasRoutedCreditTags && outputCredits.isEmpty()
                            && consumerId != null && !outputSeed.isEmpty()) {
                        outputCredits = Map.of(consumerId, outputSeed);
                    }
                    if (consumerId != null
                            && (!inputSeed.isEmpty() || !initialSeed.isEmpty()
                                    || !outputCredits.isEmpty()
                                    || !sharedOutputCredits.isEmpty())
                            && details instanceof ISeedPreservingCraftingTask) {
                        details = new ExecuteLoopPattern(
                                details, consumerId, initialSeed, inputSeed,
                                outputCredits, sharedOutputCredits);
                    }
                    var task = tasks.computeIfAbsent(details, ignored -> new TaskProgress());
                    task.value = com.moakiee.thunderbolt.core.crafting.planner.Sat.add(
                            task.value, remaining);
                }
            }
        }

        private void rebuildPendingOutputs() {
            pendingOutputs.clear();
            for (var entry : tasks.entrySet()) {
                addPendingOutputs(entry.getKey(), entry.getValue().value);
            }
        }

        private void rebuildWaitingKeys() {
            waitingKeys.clear();
            for (var entry : waitingFor.list) {
                if (entry.getLongValue() > 0) {
                    waitingKeys.add(entry.getKey());
                }
            }
        }

        private boolean insertWaitingFor(AEKey what, long amount) {
            if (amount <= 0) {
                return false;
            }

            boolean wasAbsent = waitingFor.list.get(what) <= 0;
            waitingFor.insert(what, amount, Actionable.MODULATE);
            boolean added = wasAbsent && waitingFor.list.get(what) > 0;
            if (added) {
                waitingKeys.add(what);
            }
            return added;
        }

        private WaitingExtract extractWaitingFor(AEKey what, long amount) {
            if (amount <= 0) {
                return new WaitingExtract(0L, false);
            }

            long before = waitingFor.list.get(what);
            long extracted = waitingFor.extract(what, amount, Actionable.MODULATE);
            boolean removed = before > 0 && waitingFor.list.get(what) <= 0;
            if (removed) {
                waitingKeys.remove(what);
            }
            return new WaitingExtract(extracted, removed);
        }

        private boolean clearWaitingFor() {
            boolean changed = !waitingKeys.isEmpty();
            waitingFor.clear();
            waitingKeys.clear();
            return changed;
        }

        private void addPendingOutputs(IPatternDetails details, long copies) {
            if (copies <= 0) {
                return;
            }
            for (var output : details.getOutputs()) {
                pendingOutputs.add(output.what(), multiplySaturated(output.amount(), copies));
            }
        }

        private void removePendingOutputs(IPatternDetails details, long copies) {
            if (copies <= 0) {
                return;
            }
            for (var output : details.getOutputs()) {
                long amount = multiplySaturated(output.amount(), copies);
                long current = pendingOutputs.get(output.what());
                if (current <= amount) {
                    pendingOutputs.remove(output.what());
                } else {
                    pendingOutputs.remove(output.what(), amount);
                }
            }
        }

        private CompoundTag writeToNBT(HolderLookup.Provider registries) {
            var data = new CompoundTag();

            var linkData = new CompoundTag();
            link.writeToNBT(linkData);
            data.put(NBT_LINK, linkData);

            data.put(NBT_FINAL_OUTPUT, GenericStack.writeTag(registries, finalOutput));
            data.put(NBT_WAITING_FOR, waitingFor.writeToNBT(registries));
            data.put(NBT_TIME_TRACKER, timeTracker.writeToNBT());

            var list = new ListTag();
            for (var entry : tasks.entrySet()) {
                var definition = entry.getKey() instanceof CraftingTaskPersistenceDefinition persistent
                        ? persistent.craftingTaskPersistenceDefinition()
                        : entry.getKey().getDefinition();
                var item = definition.toTag(registries);
                item.putLong(NBT_CRAFTING_PROGRESS, entry.getValue().value);
                if (entry.getKey() instanceof ExecuteLoopPattern loopPattern) {
                    item.putUUID(NBT_SEED_CONSUMER, loopPattern.seedConsumerId());
                    var initialSeed = loopPattern.initialSeed();
                    var inputSeed = loopPattern.inputSeed();
                    var outputSeed = loopPattern.outputSeed();
                    var outputCredits = loopPattern.outputSeedCredits();
                    var sharedOutputCredits = loopPattern.sharedOutputSeedCredits();
                    if (!initialSeed.isEmpty()) {
                        item.put(NBT_INITIAL_SEED, writeCounter(initialSeed, registries));
                    }
                    if (!inputSeed.isEmpty()) {
                        item.put(NBT_INPUT_SEED, writeCounter(inputSeed, registries));
                    }
                    if (!outputSeed.isEmpty()) {
                        item.put(NBT_OUTPUT_SEED, writeCounter(outputSeed, registries));
                    }
                    if (!outputCredits.isEmpty()) {
                        item.put(NBT_OUTPUT_SEED_CREDITS,
                                writeSeedCredits(outputCredits, registries));
                    }
                    if (!sharedOutputCredits.isEmpty()) {
                        item.put(NBT_SHARED_OUTPUT_SEED_CREDITS,
                                writeSeedCredits(sharedOutputCredits, registries));
                    }
                }
                list.add(item);
            }
            data.put(NBT_TASKS, list);

            data.putLong(NBT_REMAINING_AMOUNT, remainingAmount);
            if (playerId != null) {
                data.putInt(NBT_PLAYER_ID, playerId);
            }
            data.putBoolean(NBT_SUSPENDED, suspended);
            data.putBoolean(NBT_SOFT_CANCELLING, softCancelling);
            data.putBoolean(NBT_CLOSED_LOOP_JOB, closedLoopJob);
            return data;
        }

        private List<ExecuteLoopPattern> loopPatterns() {
            var result = new ArrayList<ExecuteLoopPattern>();
            for (var details : tasks.keySet()) {
                if (details instanceof ExecuteLoopPattern loop) result.add(loop);
            }
            return List.copyOf(result);
        }
    }

    private record TimeWheelPlan(GenericStack finalOutput) implements ICraftingPlan {
        @Override
        public long bytes() {
            return 0;
        }

        @Override
        public boolean simulation() {
            return false;
        }

        @Override
        public boolean multiplePaths() {
            return false;
        }

        @Override
        public KeyCounter usedItems() {
            return new KeyCounter();
        }

        @Override
        public KeyCounter emittedItems() {
            return new KeyCounter();
        }

        @Override
        public KeyCounter missingItems() {
            return new KeyCounter();
        }

        @Override
        public Map<IPatternDetails, Long> patternTimes() {
            return Map.of();
        }
    }

    private record UsedItemsOverridePlan(ICraftingPlan delegate, KeyCounter usedItems)
            implements ICraftingPlan {
        @Override public GenericStack finalOutput() { return delegate.finalOutput(); }
        @Override public long bytes() { return delegate.bytes(); }
        @Override public boolean simulation() { return delegate.simulation(); }
        @Override public boolean multiplePaths() { return delegate.multiplePaths(); }
        @Override public KeyCounter emittedItems() { return delegate.emittedItems(); }
        @Override public KeyCounter missingItems() { return delegate.missingItems(); }
        @Override public Map<IPatternDetails, Long> patternTimes() { return delegate.patternTimes(); }
    }

    private final class SingleTaskBatchJobView implements BatchJobView, BatchTaskHandle, Iterator<BatchTaskHandle> {
        private TimeWheelJob activeJob;
        private IPatternDetails details;
        private boolean consumed;

        private SingleTaskBatchJobView bind(TimeWheelJob activeJob, IPatternDetails details) {
            this.activeJob = activeJob;
            this.details = details;
            this.consumed = false;
            return this;
        }

        @Override
        public Iterator<BatchTaskHandle> taskIterator() {
            consumed = false;
            return this;
        }

        @Override
        public Level level() {
            return cpu.getLevel();
        }

        @Override
        public boolean hasNext() {
            var task = activeJob.tasks.get(details);
            return !consumed && task != null && task.value > 0;
        }

        @Override
        public BatchTaskHandle next() {
            if (!hasNext()) {
                throw new java.util.NoSuchElementException();
            }
            consumed = true;
            return this;
        }

        @Override
        public void remove() {
            removeTask(activeJob, details);
            postPatternOutputsChange(details);
        }

        @Override
        public IPatternDetails details() {
            return details;
        }

        @Override
        public long getValue() {
            var task = activeJob.tasks.get(details);
            return task != null ? task.value : 0L;
        }

        @Override
        public void setValue(long value) {
            setTaskValue(activeJob, details, value);
        }

        @Override
        public ListCraftingInventory waitingFor() {
            return activeJob.waitingFor;
        }

        @Override
        public UUID craftingId() {
            return activeJob.link.getCraftingID();
        }

        @Override
        public void insertWaitingFor(AEKey what, long amount) {
            Ae2LtTimeWheelCraftingCpuLogic.this.insertWaitingFor(activeJob, what, amount);
        }

        @Override
        public void addContainerMaxItems(long count, AEKeyType type) {
            addMaxItems(activeJob.timeTracker, count, type);
        }
    }

    private static long saturatingAdd(long left, long right) {
        if (right <= 0L) return left;
        if (left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }
}
