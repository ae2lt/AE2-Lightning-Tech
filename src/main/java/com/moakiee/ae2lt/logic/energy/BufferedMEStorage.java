package com.moakiee.ae2lt.logic.energy;

import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import net.minecraft.network.chat.Component;

/**
 * Single-cell FE buffer/proxy in front of an ME storage delegate.
 *
 * <p>Mirrors AE2's {@code MEChestBlockEntity} architecture: when a Flux Cell
 * is installed, the cell IS the buffer. Every {@link #extract}/{@link #insert}
 * mutates the cell directly; the cell's internal {@code storedEnergy}
 * counter is the canonical source of truth. The cell's ItemStack-data
 * persist is deferred to {@link #endTick(AEKey, IActionSource)} so that 64+ writes
 * during a single OVERLOAD tick collapse to a single ItemStack data-component
 * update.
 *
 * <p>When no cell is installed, NORMAL-mode distribution falls back to the
 * transient {@link #feBuffer}; OVERLOAD mode short-circuits since
 * {@code BufferCapacity == 0} disables it at the host level.
 *
 * <p>If a per-tick distribution would otherwise exceed what the cell holds,
 * {@link #beginMemoryBatch} (NORMAL) and {@link #refillFromDelegateInline}
 * (OVERLOAD) refill the cell from the delegate <i>once</i>, amortizing the
 * cost across {@code 20 ticks} of expected consumption.
 */
public class BufferedMEStorage implements MEStorage {

    private static final int HISTORY_SIZE = 20;

    private static long saturatingAdd(long a, long b) {
        long r = a + b;
        return ((a ^ r) & (b ^ r)) < 0L ? Long.MAX_VALUE : r;
    }

    private static long saturatingMul(long a, long b) {
        if (a <= 0L || b <= 0L) return 0L;
        return a > Long.MAX_VALUE / b ? Long.MAX_VALUE : a * b;
    }

    private final MEStorage delegate;
    @Nullable
    private final Supplier<MEStorage> cellSupplier;
    @Nullable
    private final Runnable cellPersistCallback;
    private final long[] consumptionHistory = new long[HISTORY_SIZE];

    /** Transient preload buffer, ONLY used when no cell is installed. */
    private long feBuffer;
    private boolean batchOnly;
    private int historyPointer;
    private int costMultiplier = 1;

    public BufferedMEStorage(MEStorage delegate) {
        this(delegate, null);
    }

    public BufferedMEStorage(MEStorage delegate, @Nullable Supplier<MEStorage> cellSupplier) {
        this(delegate, cellSupplier, null);
    }

    public BufferedMEStorage(MEStorage delegate, @Nullable Supplier<MEStorage> cellSupplier,
                             @Nullable Runnable cellPersistCallback) {
        this.delegate = delegate;
        this.cellSupplier = cellSupplier;
        this.cellPersistCallback = cellPersistCallback;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (!isFeKey(what) || amount <= 0L) {
            return delegate.extract(what, amount, mode, source);
        }
        AEKey feKey = AppFluxBridge.FE_KEY;
        if (feKey == null) return 0L;

        long needed = saturatingMul(amount, costMultiplier);

        // NORMAL batch path: beginMemoryBatch already pre-extracted `pulled` FE
        // from cell into feBuffer in one shot, so the per-target modulate-pass
        // extracts all hit feBuffer and never trigger a per-call
        // FluxCellInventory.saveChanges chain.
        if (batchOnly) {
            return extractFromBuffer(needed, mode) / costMultiplier;
        }

        MEStorage cell = resolveCell();
        if (cell != null) {
            return extractThroughCell(cell, feKey, needed, mode, source) / costMultiplier;
        }

        if (feBuffer > 0L) {
            return extractFromBuffer(needed, mode) / costMultiplier;
        }
        return delegate.extract(feKey, needed, mode, source) / costMultiplier;
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (!isFeKey(what) || amount <= 0L) {
            return delegate.insert(what, amount, mode, source);
        }
        AEKey feKey = AppFluxBridge.FE_KEY;
        if (feKey == null) return delegate.insert(what, amount, mode, source);

        long credit = saturatingMul(amount, costMultiplier);
        long accepted;

        // NORMAL batch path: leftover from undelivered demand goes back to
        // feBuffer; endBatch will flush it back to cell or delegate in a
        // single call. Skips a per-target cell.insert + saveChanges chain.
        // recordReturn rebalances the history bucket: extractFromBuffer
        // already recorded `extracted` as consumed in this tick, but the
        // leftover never actually left the system, so we cancel that slice.
        if (batchOnly) {
            if (mode == Actionable.MODULATE && credit > 0L) {
                feBuffer = saturatingAdd(feBuffer, credit);
                recordReturn(credit);
            }
            return credit / costMultiplier;
        }

        MEStorage cell = resolveCell();
        if (cell != null) {
            long intoCell = cell.insert(feKey, credit, mode, source);
            long overflow = credit - intoCell;
            long intoDelegate = overflow > 0L ? delegate.insert(feKey, overflow, mode, source) : 0L;
            accepted = saturatingAdd(intoCell, intoDelegate);
            if (mode == Actionable.MODULATE && intoCell > 0L) {
                recordReturn(intoCell);
            }
        } else if (feBuffer > 0L) {
            accepted = credit;
            if (mode == Actionable.MODULATE && credit > 0L) {
                feBuffer = saturatingAdd(feBuffer, credit);
                recordReturn(credit);
            }
        } else {
            accepted = delegate.insert(feKey, credit, mode, source);
        }

        return accepted / costMultiplier;
    }

    @Override
    public Component getDescription() {
        return delegate.getDescription();
    }

    // ---- direct-send (OVERLOAD) -----------------------------------------

    /**
     * OVERLOAD path. Pull up to {@code amount} FE straight from the cell —
     * <b>no inline refill</b>. The caller in {@code sendToTargetRepeatedOptimistic}
     * is responsible for triggering refill on shortfall by computing a
     * single-call demand via {@code simulateReceive} and feeding it through
     * {@link #refillBudgetForDirectSend} → {@link #refillForDirectSend},
     * because {@code amount} here is the <i>remaining batch budget</i>
     * ({@code maxFe × remainingCalls × cost}), not a single call's demand.
     * Doing inline refill here would size the refill against the entire
     * remaining batch and pull the network dry / fill the cell to the brim
     * every call.
     */
    public long extractForDirectSend(long amount, IActionSource source) {
        AEKey feKey = AppFluxBridge.FE_KEY;
        if (feKey == null || amount <= 0L) return 0L;
        long needed = saturatingMul(amount, costMultiplier);

        MEStorage cell = resolveCell();
        if (cell != null) {
            long extracted = cell.extract(feKey, needed, Actionable.MODULATE, source);
            long credited = extracted / costMultiplier;
            long consumed = credited * costMultiplier;
            long remainder = extracted - consumed;
            if (remainder > 0L) {
                long returned = cell.insert(feKey, remainder, Actionable.MODULATE, source);
                if (returned < remainder) {
                    delegate.insert(feKey, remainder - returned, Actionable.MODULATE, source);
                }
            }
            if (consumed > 0L) recordConsumption(consumed);
            return credited;
        }
        if (batchOnly || feBuffer > 0L) {
            long extracted = Math.min(needed, feBuffer);
            long credited = extracted / costMultiplier;
            long consumed = credited * costMultiplier;
            feBuffer -= consumed;
            if (consumed > 0L) recordConsumption(consumed);
            return credited;
        }
        return 0L;
    }

    public long returnFromDirectSend(long amount, IActionSource source) {
        AEKey feKey = AppFluxBridge.FE_KEY;
        if (feKey == null || amount <= 0L) return 0L;
        long credit = saturatingMul(amount, costMultiplier);
        long accepted;

        MEStorage cell = resolveCell();
        if (cell != null) {
            long intoCell = cell.insert(feKey, credit, Actionable.MODULATE, source);
            long overflow = credit - intoCell;
            long intoDelegate = overflow > 0L
                    ? delegate.insert(feKey, overflow, Actionable.MODULATE, source) : 0L;
            accepted = saturatingAdd(intoCell, intoDelegate);
        } else if (batchOnly || feBuffer > 0L) {
            feBuffer = saturatingAdd(feBuffer, credit);
            accepted = credit;
        } else {
            accepted = delegate.insert(feKey, credit, Actionable.MODULATE, source);
        }
        if (accepted > 0L) recordReturn(accepted);
        return accepted / costMultiplier;
    }

    /**
     * @return the budget the OVERLOAD path is allowed to refill into the cell
     *         when its energy runs short — current per-call cost plus 19
     *         ticks of historical consumption (the legacy 20-tick lookahead).
     */
    public long refillBudgetForDirectSend(long currentDemand) {
        if (currentDemand <= 0L || resolveCell() == null) return 0L;
        long currentCost = saturatingMul(currentDemand, costMultiplier);
        return saturatingAdd(currentCost, recentConsumptionBeforeCurrentTick());
    }

    public void refillForDirectSend(long refillBudget, IActionSource source) {
        AEKey feKey = AppFluxBridge.FE_KEY;
        MEStorage cell = resolveCell();
        if (feKey == null || refillBudget <= 0L || cell == null) return;
        refillFromDelegateInline(cell, feKey, refillBudget, source);
    }

    // ---- batch (NORMAL) -------------------------------------------------

    /**
     * Pre-tick refill: ensure the cell holds at least {@code demand} FE so the
     * subsequent distribution loop can extract from cell without further
     * delegate calls. With no cell installed, falls back to the transient
     * {@link #feBuffer} (one delegate.extract).
     *
     * @return amount actually available for distribution this tick (might be
     *         less than {@code demand} if neither cell nor delegate can cover
     *         it).
     */
    public long beginMemoryBatch(AEKey feKey, long demand, IActionSource source) {
        if (demand <= 0L) return 0L;
        batchOnly = true;

        MEStorage cell = resolveCell();
        if (cell != null) {
            // Single cell.extract MODULATE for the entire NORMAL batch instead
            // of one per target. Saves N - 1 trips through
            // FluxCellInventory.saveChanges → callback → BE.saveChanges →
            // Level.blockEntityChanged → TickHandler.addCallable.
            long pulled = cell.extract(feKey, demand, Actionable.MODULATE, source);
            if (pulled < demand) {
                long shortfall = demand - pulled;
                long ahead = recentConsumptionBeforeCurrentTick();
                long ideal = saturatingAdd(shortfall, ahead);
                refillFromDelegateInline(cell, feKey, ideal, source);
                long retry = cell.extract(feKey, shortfall, Actionable.MODULATE, source);
                pulled += retry;
            }
            if (pulled > 0L) {
                feBuffer = saturatingAdd(feBuffer, pulled);
            }
            return pulled;
        }

        // No cell — pull straight from the ME network into feBuffer.
        long pulled = delegate.extract(feKey, demand, Actionable.MODULATE, source);
        if (pulled > 0L) {
            feBuffer = saturatingAdd(feBuffer, pulled);
        }
        return pulled;
    }

    public long endBatch(AEKey feKey, IActionSource source) {
        try {
            if (feBuffer <= 0L) return 0L;

            // Anything left in feBuffer after the modulate pass is FE that
            // beginMemoryBatch over-pulled (target rejected its slice mid-loop
            // or demand shrank). Return it to the cell — or, if no cell is
            // installed, to the delegate — in a single insert call.
            MEStorage cell = resolveCell();
            if (cell != null) {
                long intoCell = cell.insert(feKey, feBuffer, Actionable.MODULATE, source);
                long overflow = feBuffer - intoCell;
                long intoDelegate = overflow > 0L
                        ? delegate.insert(feKey, overflow, Actionable.MODULATE, source) : 0L;
                long absorbed = intoCell + intoDelegate;
                feBuffer -= absorbed;
                // recordConsumption was called in extractFromBuffer for what
                // actually went out; what came back never left the system, so
                // there is nothing to unrecord here.
                return absorbed;
            }
            long returned = delegate.insert(feKey, feBuffer, Actionable.MODULATE, source);
            feBuffer -= returned;
            return returned;
        } finally {
            batchOnly = false;
        }
    }

    public long flush(AEKey feKey, IActionSource source) {
        if (feBuffer <= 0L) return 0L;
        // Used only by lifecycle paths (endTick / flushAll). When a cell is
        // installed we prefer to keep the FE in the cell rather than dump it
        // back to the network so the player's installed disk reflects reality.
        MEStorage cell = resolveCell();
        if (cell != null) {
            long intoCell = cell.insert(feKey, feBuffer, Actionable.MODULATE, source);
            long overflow = feBuffer - intoCell;
            long intoDelegate = overflow > 0L
                    ? delegate.insert(feKey, overflow, Actionable.MODULATE, source) : 0L;
            long absorbed = intoCell + intoDelegate;
            feBuffer -= absorbed;
            return absorbed;
        }
        long returned = delegate.insert(feKey, feBuffer, Actionable.MODULATE, source);
        feBuffer -= returned;
        return returned;
    }

    /**
     * Tick-end synchronisation point. Flushes any orphan {@link #feBuffer}
     * back to the delegate (defensive — should already be empty if
     * {@link #endBatch} ran), then invokes the persist callback so the host
     * can write the cell's storedEnergy into the ItemStack data component.
     *
     * <p>The callback is responsible for both the cell's persist and the
     * host's setChanged; this class deliberately avoids touching either so
     * we don't double-write the ItemStack and don't couple to AppFlux types.
     */
    public long endTick(AEKey feKey, IActionSource source) {
        long flushed = feBuffer > 0L ? flush(feKey, source) : 0L;
        if (cellPersistCallback != null && resolveCell() != null) {
            cellPersistCallback.run();
        }
        return flushed;
    }

    /**
     * Lifecycle cleanup (cell removed, mode switched, BE removed, chunk
     * unloaded). Identical to {@link #endTick} but also clears the transient
     * buffer just in case.
     */
    public long flushAll(AEKey feKey, IActionSource source) {
        long flushed = endTick(feKey, source);
        clearBuffer();
        return flushed;
    }

    public void advanceHistory() {
        historyPointer = (historyPointer + 1) % consumptionHistory.length;
        consumptionHistory[historyPointer] = 0L;
    }

    public void setCostMultiplier(int multiplier) {
        costMultiplier = Math.max(1, multiplier);
    }

    public long getBufferedEnergy() {
        MEStorage cell = resolveCell();
        if (cell != null) {
            AEKey feKey = AppFluxBridge.FE_KEY;
            if (feKey == null) return 0L;
            return cell.extract(feKey, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
        }
        return feBuffer;
    }

    /**
     * Clears the transient preload buffer WITHOUT returning FE to the delegate.
     * No-op when a cell is providing backing storage. Prefer {@link
     * #flushAll(AEKey, IActionSource)} for lifecycle cleanup.
     */
    public void clearBuffer() {
        feBuffer = 0L;
    }

    // ---- helpers --------------------------------------------------------

    /**
     * Cell-aware extract with inline refill on shortfall. Mirrors the AE2 ME
     * Chest pattern: whenever the cell can't satisfy a demand, top it up from
     * the delegate (ME network) once, then retry. Refill size includes the
     * 20-tick consumption lookahead so we minimise delegate calls during
     * sustained loads.
     */
    private long extractThroughCell(MEStorage cell, AEKey feKey, long needed, Actionable mode,
                                    IActionSource source) {
        long extracted = cell.extract(feKey, needed, mode, source);
        if (extracted >= needed) {
            if (mode == Actionable.MODULATE && extracted > 0L) recordConsumption(extracted);
            return extracted;
        }

        // Only MODULATE is allowed to refill — SIMULATE must remain side-effect-free.
        if (mode == Actionable.MODULATE) {
            long shortfall = needed - extracted;
            long ahead = recentConsumptionBeforeCurrentTick();
            long ideal = saturatingAdd(shortfall, ahead);
            refillFromDelegateInline(cell, feKey, ideal, source);
            long retry = cell.extract(feKey, shortfall, Actionable.MODULATE, source);
            extracted += retry;
        }
        if (mode == Actionable.MODULATE && extracted > 0L) recordConsumption(extracted);
        return extracted;
    }

    /**
     * Pull up to {@code request} FE from the delegate into the cell, capped
     * at the cell's current free space. If the cell rejects part of the
     * delivery (race), the rejected amount goes back to the delegate so we
     * never silently void energy.
     */
    private void refillFromDelegateInline(MEStorage cell, AEKey feKey, long request,
                                          IActionSource source) {
        if (request <= 0L) return;
        long freeSpace = cell.insert(feKey, Long.MAX_VALUE, Actionable.SIMULATE, source);
        long refill = Math.min(request, freeSpace);
        if (refill <= 0L) return;
        long pulled = delegate.extract(feKey, refill, Actionable.MODULATE, source);
        if (pulled <= 0L) return;
        long inserted = cell.insert(feKey, pulled, Actionable.MODULATE, source);
        if (inserted < pulled) {
            delegate.insert(feKey, pulled - inserted, Actionable.MODULATE, source);
        }
    }

    private long extractFromBuffer(long needed, Actionable mode) {
        if (mode == Actionable.SIMULATE) {
            return Math.min(needed, feBuffer);
        }
        long extracted = Math.min(needed, feBuffer);
        feBuffer -= extracted;
        if (extracted > 0L) recordConsumption(extracted);
        return extracted;
    }

    @Nullable
    private MEStorage resolveCell() {
        return cellSupplier != null ? cellSupplier.get() : null;
    }

    private long recentConsumptionBeforeCurrentTick() {
        long sum = 0L;
        for (int i = 0; i < consumptionHistory.length; i++) {
            if (i != historyPointer) {
                sum = saturatingAdd(sum, consumptionHistory[i]);
            }
        }
        return sum;
    }

    private void recordConsumption(long amount) {
        consumptionHistory[historyPointer] = saturatingAdd(consumptionHistory[historyPointer], amount);
    }

    private void recordReturn(long amount) {
        consumptionHistory[historyPointer] = Math.max(0L, consumptionHistory[historyPointer] - amount);
    }

    private boolean isFeKey(AEKey what) {
        AEKey feKey = AppFluxBridge.FE_KEY;
        return feKey != null && feKey.equals(what);
    }
}
