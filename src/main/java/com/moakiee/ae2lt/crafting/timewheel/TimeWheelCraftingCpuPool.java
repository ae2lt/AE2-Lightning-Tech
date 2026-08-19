package com.moakiee.ae2lt.crafting.timewheel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.config.Actionable;
import appeng.api.config.CpuSelectionMode;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CraftingJobStatus;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.me.service.CraftingService;
import appeng.hooks.ticking.TickHandler;

import com.moakiee.thunderbolt.core.crafting.batch.TickProviderDispatchSchedule;
import com.moakiee.thunderbolt.core.crafting.plan.LoopCraftingPlan;
import com.moakiee.thunderbolt.api.crafting.cpu.ExtendedCraftingCpuCluster;

/**
 * Shared-capacity time-wheel CPU that creates one virtual CPU per crafting job.
 */
public final class TimeWheelCraftingCpuPool implements ExtendedCraftingCpuCluster {
    private static final int PRODUCTIVE_DISPATCH_QUANTUM = 32;
    private static final int DATA_VERSION = 1;
    private static final String TAG_VERSION = "version";
    private static final String TAG_CPUS = "cpus";
    private static final String TAG_ID = "id";
    private static final String TAG_RESERVED_BYTES = "reservedBytes";
    private static final String TAG_STATE = "state";

    private final TimeWheelCraftingCpuPoolHost host;
    private final Map<UUID, PoolEntry> activeCpus = new LinkedHashMap<>();

    private long totalStorage;
    private int sharedCoProcessors;
    private long maxCopiesPerTick;
    private boolean unboundedBatch;
    private boolean fastPlanningEnabled = true;
    private long remainingStorage;
    private boolean cpuListChanged;
    private final TickProviderDispatchSchedule dispatchSchedule = new TickProviderDispatchSchedule();

    public TimeWheelCraftingCpuPool(TimeWheelCraftingCpuPoolHost host,
                                    long totalStorage,
                                    int sharedCoProcessors,
                                    long maxCopiesPerTick,
                                    boolean unboundedBatch) {
        if (totalStorage < 0L) {
            throw new IllegalArgumentException("Total crafting storage must not be negative.");
        }
        if (sharedCoProcessors < 0) {
            throw new IllegalArgumentException("Shared co-processors must not be negative.");
        }
        this.host = host;
        this.totalStorage = totalStorage;
        this.sharedCoProcessors = sharedCoProcessors;
        this.maxCopiesPerTick = Math.max(1L, maxCopiesPerTick);
        this.unboundedBatch = unboundedBatch;
        this.remainingStorage = totalStorage;
    }

    public TimeWheelCraftingCpuPoolHost getHost() {
        return host;
    }

    @Override
    public boolean isFastPlanningEnabled() {
        return fastPlanningEnabled;
    }

    @Override
    public int getCpuPriority() {
        return host == null ? 0 : host.getCpuPriority();
    }

    public void setFastPlanningEnabled(boolean enabled) {
        fastPlanningEnabled = enabled;
    }

    public void reconfigure(long totalStorage,
                            int sharedCoProcessors,
                            long maxCopiesPerTick,
                            boolean unboundedBatch) {
        if (!activeCpus.isEmpty()) {
            throw new IllegalStateException("Cannot reconfigure a time-wheel CPU pool with retained state.");
        }
        if (totalStorage < 0L) {
            throw new IllegalArgumentException("Total crafting storage must not be negative.");
        }
        if (sharedCoProcessors < 0) {
            throw new IllegalArgumentException("Shared co-processors must not be negative.");
        }
        this.totalStorage = totalStorage;
        this.sharedCoProcessors = sharedCoProcessors;
        this.maxCopiesPerTick = Math.max(1L, maxCopiesPerTick);
        this.unboundedBatch = unboundedBatch;
        this.remainingStorage = totalStorage;
        this.cpuListChanged = true;
        host.markCpuDirty();
    }

    @Override
    public List<TimeWheelCraftingCPU> getActiveCpus() {
        var result = new ArrayList<TimeWheelCraftingCPU>(activeCpus.size());
        for (var entry : activeCpus.values()) {
            result.add(entry.cpu());
        }
        return List.copyOf(result);
    }

    @Override
    public long tickCraftingLogic(IEnergyService energyService, ICraftingService craftingService) {
        if (!(craftingService instanceof CraftingService concreteCraftingService)) {
            throw new IllegalArgumentException(
                    "TimeWheel requires AE2's concrete CraftingService implementation");
        }
        resolvePendingLoad();
        var latestChange = new long[] {Long.MIN_VALUE};
        int successfulDispatchBudget = sharedCoProcessors >= Integer.MAX_VALUE - 1
                ? Integer.MAX_VALUE : sharedCoProcessors + 1;
        dispatchSchedule.beginTick(TickHandler.instance().getCurrentTick());

        var scheduledCpus = new ArrayList<ScheduledCpu>(activeCpus.size());
        for (var entry : List.copyOf(activeCpus.values())) {
            scheduledCpus.add(new ScheduledCpu(
                    entry,
                    unboundedBatch ? Long.MAX_VALUE : maxCopiesPerTick));
        }

        try {
            ProductiveDispatchScheduler.run(
                    successfulDispatchBudget,
                    PRODUCTIVE_DISPATCH_QUANTUM,
                    scheduledCpus,
                    (scheduled, allowance) -> {
                        if (scheduled.remainingCopies <= 0L) return 0;
                        var usage = tickScheduledCpu(
                                scheduled, allowance, energyService, concreteCraftingService);
                        latestChange[0] = Math.max(
                                latestChange[0],
                                scheduled.entry.cpu().getCraftingLogic().getWaitingKeysModifiedOnTick());
                        return usage.successfulDispatches();
                    });
        } finally {
            // ProductiveDispatchScheduler may revisit one virtual CPU several times in this
            // physical tick. Only roll its still-due tasks forward after every visit is done.
            for (var scheduled : scheduledCpus) {
                scheduled.entry.cpu().getCraftingLogic().finishPhysicalSchedulingTick();
            }
        }
        rotateSchedulingOrder();
        removeDrainedCpus();
        return latestChange[0];
    }

    private Ae2LtTimeWheelCraftingCpuLogic.TickUsage tickScheduledCpu(
            ScheduledCpu scheduled,
            int dispatchBudget,
            IEnergyService energyService,
            CraftingService craftingService) {
        var usage = scheduled.entry.cpu().getCraftingLogic().tickCraftingLogic(
                energyService,
                craftingService,
                dispatchBudget,
                scheduled.remainingCopies,
                dispatchSchedule);
        if (scheduled.remainingCopies != Long.MAX_VALUE) {
            scheduled.remainingCopies = Math.max(
                    0L,
                    scheduled.remainingCopies - usage.dispatchedCopies());
        }
        return usage;
    }

    private void rotateSchedulingOrder() {
        if (activeCpus.size() <= 1) return;
        var iterator = activeCpus.entrySet().iterator();
        if (!iterator.hasNext()) return;
        var first = iterator.next();
        UUID id = first.getKey();
        PoolEntry entry = first.getValue();
        iterator.remove();
        activeCpus.put(id, entry);
    }

    @Override
    public void addWaitingKeys(Set<AEKey> waitingKeys) {
        for (var entry : activeCpus.values()) {
            entry.cpu().getCraftingLogic().getAllWaitingFor(waitingKeys);
        }
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode) {
        long inserted = 0L;
        for (var entry : activeCpus.values()) {
            if (inserted >= amount) {
                break;
            }
            inserted += entry.cpu().getCraftingLogic().insert(what, amount - inserted, mode);
        }
        return inserted;
    }

    @Override
    public long getRequestedAmount(AEKey what) {
        long requested = 0L;
        for (var entry : activeCpus.values()) {
            requested = saturatingAdd(requested, entry.cpu().getCraftingLogic().getWaitingFor(what));
        }
        return requested;
    }

    @Override
    public void restoreCraftingLinks(Consumer<ICraftingLink> consumer) {
        for (var entry : activeCpus.values()) {
            var maybeLink = entry.cpu().getCraftingLogic().getLastLink();
            if (maybeLink instanceof CraftingLink link) {
                consumer.accept(link);
            }
        }
    }

    @Override
    public boolean consumeCpuListChanged() {
        boolean changed = cpuListChanged;
        cpuListChanged = false;
        return changed;
    }

    @Override
    public ICraftingSubmitResult submitJob(IGrid grid,
                                            ICraftingPlan plan,
                                            IActionSource src,
                                            @Nullable ICraftingRequester requester) {
        if (!isActive() || !canHandle(plan)) {
            return CraftingSubmitResult.CPU_OFFLINE;
        }

        boolean infiniteStorage = hasInfiniteStorage();
        long reservedBytes = infiniteStorage ? 0L : Math.max(0L, plan.bytes());
        if (!infiniteStorage && reservedBytes > remainingStorage) {
            return CraftingSubmitResult.CPU_TOO_SMALL;
        }

        var id = UUID.randomUUID();
        long cpuStorage = infiniteStorage ? Long.MAX_VALUE : reservedBytes;
        var cpu = new TimeWheelCraftingCPU(
                host, cpuStorage, sharedCoProcessors, maxCopiesPerTick, unboundedBatch);
        var entry = new PoolEntry(id, reservedBytes, cpu);
        activeCpus.put(id, entry);
        remainingStorage -= reservedBytes;

        var result = cpu.submitJob(grid, plan, src, requester);
        if (!result.successful()) {
            if (!cpu.hasPersistentState()) {
                activeCpus.remove(id);
                recalculateRemainingStorage();
            } else {
                cpuListChanged = true;
                host.markCpuDirty();
            }
            return result;
        }

        cpuListChanged = true;
        host.markCpuDirty();
        return result;
    }

    @Override
    public boolean canHandle(ICraftingPlan plan) {
        if (plan instanceof LoopCraftingPlan loopPlan) {
            return loopPlan.canRunOn(host);
        }
        return ExtendedCraftingCpuCluster.super.canHandle(plan);
    }

    public void cancelAll() {
        for (var entry : List.copyOf(activeCpus.values())) {
            entry.cpu().cancelJob();
        }
        removeDrainedCpus();
    }

    /**
     * Best-effort release used before a host controller is removed. Ordinary
     * jobs and already-held contents are returned to host storage/the ME
     * network; a closed-loop job waiting for reusable seeds remains serialized.
     */
    public void tryReleaseContents() {
        for (var entry : List.copyOf(activeCpus.values())) {
            entry.cpu().tryReleaseContents();
        }
        removeDrainedCpus();
    }

    public boolean hasPersistentState() {
        return !activeCpus.isEmpty();
    }

    public void writeToNBT(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(TAG_VERSION, DATA_VERSION);
        var cpuList = new ListTag();
        for (var entry : activeCpus.values()) {
            if (!entry.cpu().hasPersistentState()) {
                continue;
            }
            var state = new CompoundTag();
            entry.cpu().writeToNBT(state, registries);

            var entryTag = new CompoundTag();
            entryTag.putUUID(TAG_ID, entry.id());
            entryTag.putLong(TAG_RESERVED_BYTES, entry.reservedBytes());
            entryTag.put(TAG_STATE, state);
            cpuList.add(entryTag);
        }
        if (!cpuList.isEmpty()) {
            tag.put(TAG_CPUS, cpuList);
        }
    }

    public void readFromNBT(CompoundTag tag, HolderLookup.Provider registries) {
        activeCpus.clear();
        remainingStorage = totalStorage;
        cpuListChanged = false;

        var cpuList = tag.getList(TAG_CPUS, Tag.TAG_COMPOUND);
        for (int i = 0; i < cpuList.size(); i++) {
            var entryTag = cpuList.getCompound(i);
            if (!entryTag.hasUUID(TAG_ID)
                    || !entryTag.contains(TAG_RESERVED_BYTES, Tag.TAG_LONG)
                    || !entryTag.contains(TAG_STATE, Tag.TAG_COMPOUND)) {
                continue;
            }

            var id = entryTag.getUUID(TAG_ID);
            if (activeCpus.containsKey(id)) {
                id = UUID.randomUUID();
            }
            boolean infiniteStorage = hasInfiniteStorage();
            long reservedBytes = infiniteStorage ? 0L : Math.max(0L, entryTag.getLong(TAG_RESERVED_BYTES));
            long cpuStorage = infiniteStorage ? Long.MAX_VALUE : reservedBytes;
            var cpu = new TimeWheelCraftingCPU(
                    host, cpuStorage, sharedCoProcessors, maxCopiesPerTick, unboundedBatch);
            cpu.readFromNBT(entryTag.getCompound(TAG_STATE), registries);
            activeCpus.put(id, new PoolEntry(id, reservedBytes, cpu));
        }
        recalculateRemainingStorage();
        cpuListChanged = !activeCpus.isEmpty();
    }

    @Override
    public void prepareForCraftingService() {
        resolvePendingLoad();
    }

    public void resolvePendingLoad() {
        for (var entry : activeCpus.values()) {
            entry.cpu().resolvePendingLoad();
        }
        removeDrainedCpus();
    }

    public void addRemovalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        for (var entry : activeCpus.values()) {
            entry.cpu().addRemovalDrops(level, pos, drops);
        }
    }

    public void clearRemovedContent() {
        for (var entry : activeCpus.values()) {
            entry.cpu().clearRemovedContent();
        }
        activeCpus.clear();
        remainingStorage = totalStorage;
        cpuListChanged = true;
    }

    public void invalidate(Level level, BlockPos pos, List<ItemStack> drops) {
        addRemovalDrops(level, pos, drops);
        clearRemovedContent();
        host.markCpuDirty();
    }

    @Override
    public boolean isBusy() {
        return false;
    }

    @Nullable
    @Override
    public CraftingJobStatus getJobStatus() {
        return null;
    }

    @Override
    public void cancelJob() {
    }

    @Override
    public long getAvailableStorage() {
        return remainingStorage;
    }

    public long getTotalStorage() {
        return totalStorage;
    }

    public boolean hasInfiniteStorage() {
        return totalStorage == Long.MAX_VALUE;
    }

    @Override
    public int getCoProcessors() {
        return sharedCoProcessors;
    }

    public long getMaxCopiesPerTick() {
        return maxCopiesPerTick;
    }

    public boolean hasUnboundedBatch() {
        return unboundedBatch;
    }

    @Nullable
    @Override
    public Component getName() {
        return host.getCpuDisplayName();
    }

    @Override
    public CpuSelectionMode getSelectionMode() {
        return host.getSelectionMode();
    }

    @Override
    public boolean isActive() {
        return host.isCpuActive();
    }

    private void removeDrainedCpus() {
        boolean changed = false;
        var iterator = activeCpus.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next().getValue();
            if (entry.cpu().hasPersistentState()) {
                continue;
            }
            iterator.remove();
            changed = true;
        }
        if (changed) {
            recalculateRemainingStorage();
            cpuListChanged = true;
            host.markCpuDirty();
        }
    }

    private void recalculateRemainingStorage() {
        long remaining = totalStorage;
        for (var entry : activeCpus.values()) {
            long reserved = entry.reservedBytes();
            remaining = reserved >= remaining ? 0L : remaining - reserved;
            if (remaining == 0L) {
                break;
            }
        }
        remainingStorage = remaining;
    }

    private static long saturatingAdd(long left, long right) {
        if (right <= 0L) {
            return left;
        }
        return left >= Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private record PoolEntry(UUID id, long reservedBytes, TimeWheelCraftingCPU cpu) {
    }

    private static final class ScheduledCpu {
        private final PoolEntry entry;
        private long remainingCopies;

        private ScheduledCpu(PoolEntry entry, long remainingCopies) {
            this.entry = entry;
            this.remainingCopies = remainingCopies;
        }
    }
}
