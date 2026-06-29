package com.moakiee.ae2lt.logic.timewheelcpu;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import com.google.common.base.Preconditions;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
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

import com.moakiee.ae2lt.logic.batch.BatchExecutor;
import com.moakiee.ae2lt.logic.batch.BatchJobView;
import com.moakiee.ae2lt.logic.batch.BatchTaskHandle;
import com.moakiee.ae2lt.mixin.ElapsedTimeTrackerAccessor;
import com.moakiee.ae2lt.overload.cpu.OverloadClaimResult;
import com.moakiee.ae2lt.overload.cpu.OverloadCpuStateManager;
import com.moakiee.ae2lt.overload.cpu.OverloadPatternReference;
import com.moakiee.ae2lt.overload.pattern.OverloadedProviderOnlyPatternDetails;

public final class Ae2LtTimeWheelCraftingCpuLogic {
    private static final int WHEEL_SIZE = 64;
    private static final int WHEEL_MASK = WHEEL_SIZE - 1;
    private static final int MAX_TASK_PROBES_PER_TICK = 262_144;
    private static final int RETRY_DELAY_TICKS = 4;
    private static final String TAG_INVENTORY = "inventory";
    private static final String TAG_JOB = "job";
    private static final String TAG_OVERLOAD_STATE = "ae2ltOverloadState";

    private static final String NBT_LINK = "link";
    private static final String NBT_PLAYER_ID = "playerId";
    private static final String NBT_FINAL_OUTPUT = "finalOutput";
    private static final String NBT_WAITING_FOR = "waitingFor";
    private static final String NBT_TIME_TRACKER = "timeTracker";
    private static final String NBT_REMAINING_AMOUNT = "remainingAmount";
    private static final String NBT_TASKS = "tasks";
    private static final String NBT_SUSPENDED = "suspended";
    private static final String NBT_CRAFTING_PROGRESS = "#craftingProgress";

    private final TimeWheelCraftingCPU cpu;
    private final ListCraftingInventory inventory = new ListCraftingInventory(this::postChange);
    private final Set<Consumer<AEKey>> listeners = new HashSet<>();
    private final Map<IPatternDetails, IdentityHashMap<ICraftingProvider, Boolean>> batchedByTask = new HashMap<>();
    private final Map<IPatternDetails, SinglePushProviderCursor> singlePushProviderCursors = new HashMap<>();
    private final ArrayDeque<IPatternDetails>[] taskWheel = createWheel();
    private final Set<IPatternDetails> queuedTasks = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<AEKey> batchedStatusChanges = new HashSet<>();
    private final Set<IPatternDetails> nonBatchTasksThisTick = Collections.newSetFromMap(new IdentityHashMap<>());
    private final KeyCounter scratchExpectedOutputs = new KeyCounter();
    private final KeyCounter scratchExpectedContainerItems = new KeyCounter();

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
    private boolean queueRebuildNeeded = true;
    private boolean cantStoreItems;
    private boolean batchingStatusChanges;

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

        var missingIngredient = CraftingCpuHelper.tryExtractInitialItems(plan, grid, inventory, src);
        if (missingIngredient != null) {
            return CraftingSubmitResult.missingIngredient(missingIngredient);
        }

        var playerId = src.player()
                .map(p -> p instanceof ServerPlayer serverPlayer ? IPlayerRegistry.getPlayerId(serverPlayer) : null)
                .orElse(null);
        var craftId = UUID.randomUUID();
        var linkCpu = new CraftingLink(CraftingCpuHelper.generateLinkData(craftId, requester == null, false), cpu);
        this.job = new TimeWheelJob(plan, this::postChange, linkCpu, playerId);
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
        resolvePendingLoad();
        if (this.pendingJobTag != null) {
            return;
        }
        if (!cpu.isActive()) {
            return;
        }

        long now = TickHandler.instance().getCurrentTick();
        if (now != batchTick) {
            batchTick = now;
            batchedByTask.clear();
            nonBatchTasksThisTick.clear();
            singlePushProviderCursors.clear();
        }

        cantStoreItems = false;
        if (this.job == null) {
            storeItems();
            if (!this.inventory.list.isEmpty()) {
                cantStoreItems = true;
            }
            return;
        }

        if (job.link.isCanceled()) {
            cancel();
            return;
        }

        if (job.suspended) {
            return;
        }

        var level = cpu.getLevel();
        if (level == null) {
            return;
        }

        int remainingOperations = Math.max(1, cpu.getCoProcessors() + 1);
        executeCrafting(remainingOperations, craftingService, energyService, level);
    }

    public int executeCrafting(int maxOps, CraftingService craftingService, IEnergyService energyService,
                               Level level) {
        var activeJob = this.job;
        if (activeJob == null || maxOps <= 0) {
            return 0;
        }

        prepareScheduler(activeJob);

        int usedOps = 0;
        int probes = 0;
        int probeBudget = (int) Math.min(Math.max(1024L, (long) maxOps * 2L), MAX_TASK_PROBES_PER_TICK);

        beginStatusChangeBatch();
        try {
            while (usedOps < maxOps && probes < probeBudget) {
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
                var batchResult = runBatchForTask(details, remainingOps, craftingService, energyService, level);
                if (batchResult.consumedCpuOps() > 0) {
                    usedOps += batchResult.consumedCpuOps();
                    rescheduleIfStillPending(activeJob, details, 0);
                    continue;
                }

                var outcome = pushOnePattern(activeJob, task, details, craftingService, energyService, level);
                if (outcome == DispatchOutcome.PUSHED) {
                    usedOps++;
                    rescheduleIfStillPending(activeJob, details, 0);
                } else {
                    rescheduleIfStillPending(
                            activeJob,
                            details,
                            outcome == DispatchOutcome.RETRY_LATER ? RETRY_DELAY_TICKS : 1);
                }
            }
        } finally {
            carryOverDueTasks();
            endStatusChangeBatch();
        }

        return usedOps;
    }

    private BatchExecutor.BatchRunResult runBatchForTask(IPatternDetails details,
                                                         int remainingOps,
                                                         CraftingService craftingService,
                                                         IEnergyService energyService,
                                                         Level level) {
        var activeJob = this.job;
        if (activeJob == null) {
            return BatchExecutor.BatchRunResult.EMPTY;
        }
        if (nonBatchTasksThisTick.contains(details)) {
            return BatchExecutor.BatchRunResult.EMPTY;
        }

        var result = BatchExecutor.runBatchOnly(
                remainingOps,
                craftingService,
                energyService,
                level,
                new SingleTaskBatchJobView(activeJob, details),
                inventory,
                batchedByTask,
                () -> {
                    cpu.markDirty();
                    postPatternOutputsChange(details);
                });
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
                                           Level level) {
        if (hasAmbiguousOverloadOutput(details)) {
            return DispatchOutcome.RETRY_LATER;
        }

        var expectedOutputs = scratchExpectedOutputs;
        var expectedContainerItems = scratchExpectedContainerItems;
        clearScratchCounter(expectedOutputs);
        clearScratchCounter(expectedContainerItems);
        var providers = providersForSinglePush(craftingService, details).iterator();
        if (!providers.hasNext()) {
            return DispatchOutcome.RETRY_SOON;
        }

        @Nullable
        var craftingContainer = CraftingCpuHelper.extractPatternInputs(
                details, inventory, level, expectedOutputs, expectedContainerItems);
        if (craftingContainer == null) {
            return DispatchOutcome.RETRY_LATER;
        }

        boolean pushed = false;
        try {
            var patternPower = CraftingCpuHelper.calculatePatternPower(craftingContainer);
            while (providers.hasNext()) {
                var candidate = providers.next();
                var provider = candidate.provider();
                if (provider.isBusy()) {
                    continue;
                }

                if (energyService.extractAEPower(patternPower, Actionable.SIMULATE,
                        PowerMultiplier.CONFIG) < patternPower - 0.01D) {
                    return DispatchOutcome.RETRY_LATER;
                }

                if (!provider.pushPattern(details, craftingContainer)) {
                    continue;
                }

                pushed = true;
                craftingContainer = null;
                energyService.extractAEPower(patternPower, Actionable.MODULATE, PowerMultiplier.CONFIG);
                recordPushedPattern(activeJob, details, expectedOutputs, expectedContainerItems, 1L);

                consumeTaskCopies(activeJob, details, 1L);
                advanceSinglePushProviderCursor(details, candidate.index());
                return DispatchOutcome.PUSHED;
            }
            return DispatchOutcome.RETRY_SOON;
        } finally {
            if (!pushed && craftingContainer != null) {
                CraftingCpuHelper.reinjectPatternInputs(inventory, craftingContainer);
            }
            clearScratchCounter(expectedOutputs);
            clearScratchCounter(expectedContainerItems);
        }
    }

    public long insert(AEKey what, long amount, Actionable type) {
        var activeJob = this.job;
        if (what == null || activeJob == null || amount <= 0) {
            return 0;
        }

        long strictMatched = activeJob.waitingFor.extract(what, amount, Actionable.SIMULATE);
        long acceptedStrict = Math.min(amount, strictMatched);
        long returned = 0;
        if (acceptedStrict > 0) {
            returned += acceptStrictWaitingItem(activeJob, what, acceptedStrict, type);
        }

        long overloadRemainder = Math.max(0L, amount - strictMatched);
        if (overloadRemainder <= 0 || !OverloadCpuStateManager.INSTANCE.hasAnyPending(this)) {
            return returned;
        }

        var claims = OverloadCpuStateManager.INSTANCE.claim(this, what, overloadRemainder, type);
        if (!claims.claimedAnything()) {
            return returned;
        }

        if (type == Actionable.MODULATE) {
            deductClaimedWaitingFor(activeJob, claims);
            returned += applyInventoryClaims(activeJob, what, claims);
            returned += applyRequesterClaims(activeJob, what, claims);
            cpu.markDirty();
        } else {
            returned += claims.claimedAmount();
        }

        return returned;
    }

    private long acceptStrictWaitingItem(TimeWheelJob activeJob, AEKey what, long amount, Actionable type) {
        if (type == Actionable.MODULATE) {
            decrementItems(activeJob.timeTracker, amount, what.getType());
            extractWaitingFor(activeJob, what, amount);
            cpu.markDirty();
        }

        long inserted = amount;
        if (what.matches(activeJob.finalOutput)) {
            inserted = activeJob.link.insert(what, amount, type);
            if (type == Actionable.MODULATE) {
                postChange(what);
                activeJob.remainingAmount = Math.max(0, activeJob.remainingAmount - amount);
                if (activeJob.remainingAmount <= 0) {
                    finishJob(true);
                    cpu.updateOutput(null);
                } else {
                    cpu.updateOutput(new GenericStack(activeJob.finalOutput.what(), activeJob.remainingAmount));
                }
            }
        } else if (type == Actionable.MODULATE) {
            inventory.insert(what, amount, Actionable.MODULATE);
            wakeSchedulerForReturnedInput();
        }

        return inserted;
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
        clearTaskWheel();
        storeItems();
    }

    public void cancel() {
        if (job == null) {
            return;
        }
        cpu.updateOutput(null);
        finishJob(false);
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
            var inserted = storage.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE, cpu.getSrc());
            entry.setValue(entry.getLongValue() - inserted);
        }
        this.inventory.list.removeZeros();
        cpu.markDirty();
    }

    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        this.inventory.readFromNBT(data.getList(TAG_INVENTORY, Tag.TAG_COMPOUND), registries);
        this.job = null;
        this.pendingJobTag = null;
        this.pendingOverloadTag = null;
        OverloadCpuStateManager.INSTANCE.clear(this);
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
        data.put(TAG_INVENTORY, this.inventory.writeToNBT(registries));
        if (this.job != null) {
            data.put(TAG_JOB, this.job.writeToNBT(registries));
            var overloadTag = OverloadCpuStateManager.INSTANCE.writeToTag(this, registries);
            if (overloadTag != null) {
                data.put(TAG_OVERLOAD_STATE, overloadTag);
            }
        } else if (this.pendingJobTag != null) {
            data.put(TAG_JOB, this.pendingJobTag.copy());
            if (this.pendingOverloadTag != null) {
                data.put(TAG_OVERLOAD_STATE, this.pendingOverloadTag.copy());
            }
        } else {
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
        if (this.job == null || this.job.finalOutput == null) {
            cpu.updateOutput(null);
            finishJob(false);
            return;
        }

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

    public void setJobSuspended(boolean suspended) {
        if (job != null && job.suspended != suspended) {
            job.suspended = suspended;
            cpu.markDirty();
        }
    }

    private Iterable<ProviderCandidate> providersForSinglePush(CraftingService craftingService,
                                                               IPatternDetails details) {
        var skipped = batchedByTask.get(details);
        var cursor = singlePushProviderCursors.computeIfAbsent(
                details,
                ignored -> new SinglePushProviderCursor(craftingService.getProviders(details)));
        return () -> cursor.iterator(skipped);
    }

    private void advanceSinglePushProviderCursor(IPatternDetails details, int providerIndex) {
        var cursor = singlePushProviderCursors.get(details);
        if (cursor != null) {
            cursor.advancePast(providerIndex);
        }
    }

    private boolean hasAmbiguousOverloadOutput(IPatternDetails details) {
        if (!(details instanceof OverloadedProviderOnlyPatternDetails overloadDetails)) {
            return false;
        }

        var reference = new OverloadPatternReference(
                overloadDetails.overloadPatternIdentity(),
                overloadDetails.overloadPatternDetailsView().sourcePattern());
        return OverloadCpuStateManager.INSTANCE.hasAmbiguousOutputRegistration(
                this,
                reference,
                overloadDetails.overloadPatternDetailsView());
    }

    private void recordPushedPattern(TimeWheelJob activeJob,
                                     IPatternDetails details,
                                     KeyCounter expectedOutputs,
                                     KeyCounter expectedContainerItems,
                                     long copies) {
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

        registerOverloadExpectedOutputs(activeJob, details, copies);
        cpu.markDirty();
    }

    private void registerOverloadExpectedOutputs(TimeWheelJob activeJob,
                                                 IPatternDetails details,
                                                 long copies) {
        if (!(details instanceof OverloadedProviderOnlyPatternDetails overloadDetails) || copies <= 0) {
            return;
        }

        var reference = new OverloadPatternReference(
                overloadDetails.overloadPatternIdentity(),
                overloadDetails.overloadPatternDetailsView().sourcePattern());
        var finalOutputKey = activeJob.finalOutput != null ? activeJob.finalOutput.what() : null;
        OverloadCpuStateManager.INSTANCE.registerExpectedOutputs(
                this,
                activeJob.link.getCraftingID(),
                reference,
                overloadDetails.overloadPatternDetailsView(),
                details.getOutputs(),
                finalOutputKey,
                copies);
    }

    private long applyInventoryClaims(TimeWheelJob activeJob, AEKey incoming, OverloadClaimResult claims) {
        long claimed = claims.claimedForInventory();
        if (claimed <= 0) {
            return 0;
        }

        decrementItems(activeJob.timeTracker, claimed, incoming.getType());
        inventory.insert(incoming, claimed, Actionable.MODULATE);
        wakeSchedulerForReturnedInput();
        return claimed;
    }

    private long applyRequesterClaims(TimeWheelJob activeJob, AEKey incoming, OverloadClaimResult claims) {
        long claimed = claims.claimedForRequester();
        if (claimed <= 0) {
            return 0;
        }

        decrementItems(activeJob.timeTracker, claimed, incoming.getType());
        long inserted = activeJob.link.insert(incoming, claimed, Actionable.MODULATE);
        postChange(incoming);

        activeJob.remainingAmount = Math.max(0L, activeJob.remainingAmount - claimed);
        if (activeJob.remainingAmount <= 0) {
            finishJob(true);
            cpu.updateOutput(null);
        } else {
            cpu.updateOutput(new GenericStack(activeJob.finalOutput.what(), activeJob.remainingAmount));
        }
        return inserted;
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
        if (normalized < oldValue) {
            activeJob.removePendingOutputs(details, oldValue - normalized);
        } else {
            activeJob.addPendingOutputs(details, normalized - oldValue);
        }
        postPatternOutputsChange(details);
    }

    private void removeTask(TimeWheelJob activeJob, IPatternDetails details) {
        var removed = activeJob.tasks.remove(details);
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
        if (queueRebuildNeeded || (queuedTasks.isEmpty() && !activeJob.tasks.isEmpty())) {
            rebuildTaskWheel();
        }
    }

    private void advanceWheel() {
        long now = TickHandler.instance().getCurrentTick();
        if (schedulerTick == Long.MIN_VALUE) {
            schedulerTick = now;
            return;
        }

        long delta = Math.max(0L, Math.min(WHEEL_SIZE, now - schedulerTick));
        wheelCursor = (wheelCursor + (int) delta) & WHEEL_MASK;
        schedulerTick = now;
    }

    private void rebuildTaskWheel() {
        clearTaskWheel();
        var activeJob = this.job;
        if (activeJob == null) {
            return;
        }

        for (var entry : activeJob.tasks.entrySet()) {
            if (entry.getValue().value > 0) {
                scheduleTask(entry.getKey(), 0);
            }
        }
        queueRebuildNeeded = false;
    }

    private void clearTaskWheel() {
        for (var bucket : taskWheel) {
            bucket.clear();
        }
        queuedTasks.clear();
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

    @Nullable
    private IPatternDetails pollDueTask(TimeWheelJob activeJob) {
        var bucket = taskWheel[wheelCursor];
        while (!bucket.isEmpty()) {
            var details = bucket.pollFirst();
            queuedTasks.remove(details);
            var task = activeJob.tasks.get(details);
            if (task != null && task.value > 0) {
                return details;
            }
        }
        return null;
    }

    private void rescheduleIfStillPending(TimeWheelJob activeJob, IPatternDetails details, int delayTicks) {
        var task = activeJob.tasks.get(details);
        if (task != null && task.value > 0) {
            scheduleTask(details, delayTicks);
        }
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

    private void wakeSchedulerForReturnedInput() {
        if (this.job != null) {
            this.queueRebuildNeeded = true;
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
        var changed = Set.copyOf(batchedStatusChanges);
        batchedStatusChanges.clear();
        for (var key : changed) {
            for (var listener : listeners) {
                listener.accept(key);
            }
        }
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
        ((ElapsedTimeTrackerAccessor) tracker).invokeAddMaxItems(count, type);
    }

    private static void decrementItems(ElapsedTimeTracker tracker, long count, AEKeyType type) {
        ((ElapsedTimeTrackerAccessor) tracker).invokeDecrementItems(count, type);
    }

    private static void clearScratchCounter(KeyCounter counter) {
        counter.clear();
        counter.removeEmptySubmaps();
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

    private record ProviderCandidate(ICraftingProvider provider, int index) {
    }

    private static final class SinglePushProviderCursor {
        private final ArrayList<ICraftingProvider> providers = new ArrayList<>();
        private int nextIndex;

        private SinglePushProviderCursor(Iterable<ICraftingProvider> providers) {
            for (var provider : providers) {
                this.providers.add(provider);
            }
        }

        private Iterator<ProviderCandidate> iterator(
                @Nullable IdentityHashMap<ICraftingProvider, Boolean> skipped) {
            return new Iterator<>() {
                private final int startIndex = nextIndex;
                private int visited;
                @Nullable
                private ProviderCandidate next;
                private boolean ready;

                @Override
                public boolean hasNext() {
                    int size = providers.size();
                    while (!ready && visited < size) {
                        int index = (startIndex + visited) % size;
                        visited++;

                        var provider = providers.get(index);
                        if (skipped != null && skipped.containsKey(provider)) continue;
                        if (provider.isBusy()) continue;

                        next = new ProviderCandidate(provider, index);
                        ready = true;
                    }
                    return ready;
                }

                @Override
                public ProviderCandidate next() {
                    if (!hasNext()) {
                        throw new java.util.NoSuchElementException();
                    }
                    var result = next;
                    next = null;
                    ready = false;
                    return result;
                }
            };
        }

        private void advancePast(int providerIndex) {
            int size = providers.size();
            if (size > 0) {
                nextIndex = (providerIndex + 1) % size;
            }
        }
    }

    private enum DispatchOutcome {
        PUSHED,
        RETRY_SOON,
        RETRY_LATER
    }

    private static final class TaskProgress {
        private long value;
    }

    private record WaitingExtract(long extracted, boolean removedKey) {
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
                tasks.computeIfAbsent(entry.getKey(), ignored -> new TaskProgress()).value += entry.getValue();
                addPendingOutputs(entry.getKey(), entry.getValue());
                for (var output : entry.getKey().getOutputs()) {
                    var amount = multiplySaturated(
                            multiplySaturated(output.amount(), entry.getValue()),
                            output.what().getAmountPerUnit());
                    addMaxItems(timeTracker, amount, output.what().getType());
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
            job.waitingFor.readFromNBT(data.getList(NBT_WAITING_FOR, Tag.TAG_COMPOUND), registries);
            job.rebuildWaitingKeys();
            job.tasks.clear();
            job.pendingOutputs.clear();
            job.readTasks(data.getList(NBT_TASKS, Tag.TAG_COMPOUND), registries, cpu.getLevel());
            job.rebuildPendingOutputs();
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
                var details = PatternDetailsHelper.decodePattern(pattern, level);
                var remaining = item.getLong(NBT_CRAFTING_PROGRESS);
                if (details != null && remaining > 0) {
                    tasks.computeIfAbsent(details, ignored -> new TaskProgress()).value += remaining;
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
                var item = entry.getKey().getDefinition().toTag(registries);
                item.putLong(NBT_CRAFTING_PROGRESS, entry.getValue().value);
                list.add(item);
            }
            data.put(NBT_TASKS, list);

            data.putLong(NBT_REMAINING_AMOUNT, remainingAmount);
            if (playerId != null) {
                data.putInt(NBT_PLAYER_ID, playerId);
            }
            data.putBoolean(NBT_SUSPENDED, suspended);
            return data;
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

    private final class SingleTaskBatchJobView implements BatchJobView, BatchTaskHandle, Iterator<BatchTaskHandle> {
        private final TimeWheelJob activeJob;
        private final IPatternDetails details;
        private boolean consumed;

        private SingleTaskBatchJobView(TimeWheelJob activeJob, IPatternDetails details) {
            this.activeJob = activeJob;
            this.details = details;
        }

        @Override
        public Iterator<BatchTaskHandle> taskIterator() {
            consumed = false;
            return this;
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
        public void insertWaitingFor(AEKey what, long amount) {
            Ae2LtTimeWheelCraftingCpuLogic.this.insertWaitingFor(activeJob, what, amount);
        }

        @Override
        public void addContainerMaxItems(long count, AEKeyType type) {
            addMaxItems(activeJob.timeTracker, count, type);
        }
    }
}
