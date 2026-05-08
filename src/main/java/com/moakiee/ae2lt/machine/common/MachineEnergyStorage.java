package com.moakiee.ae2lt.machine.common;

import java.util.Objects;

import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

public class MachineEnergyStorage extends SnapshotJournal<Long> implements EnergyHandler {
    private final long capacity;
    private final Runnable changeListener;

    private long storedEnergy;

    public MachineEnergyStorage(long capacity, Runnable changeListener) {
        if (capacity <= 0L) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        this.changeListener = Objects.requireNonNull(changeListener, "changeListener");
    }

    @Override
    public long getAmountAsLong() {
        return storedEnergy;
    }

    @Override
    public long getCapacityAsLong() {
        return capacity;
    }

    @Override
    public int insert(int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonNegative(amount);
        long accepted = Math.min(Integer.toUnsignedLong(amount), capacity - storedEnergy);
        if (accepted <= 0L) {
            return 0;
        }

        updateSnapshots(transaction);
        storedEnergy += accepted;
        return (int) accepted;
    }

    @Override
    public int extract(int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonNegative(amount);
        return 0;
    }

    public int extractInternal(long amount) {
        try (var transaction = Transaction.openRoot()) {
            int extracted = extractInternal(amount, transaction);
            transaction.commit();
            return extracted;
        }
    }

    public int extractInternal(long amount, TransactionContext transaction) {
        if (amount <= 0L) {
            return 0;
        }

        long extracted = Math.min(storedEnergy, Math.min(amount, Integer.MAX_VALUE));
        if (extracted <= 0L) {
            return 0;
        }

        updateSnapshots(transaction);
        storedEnergy -= extracted;
        return (int) extracted;
    }

    public long getStoredEnergyLong() {
        return storedEnergy;
    }

    public long getCapacityLong() {
        return capacity;
    }

    public void setStoredEnergy(long storedEnergy) {
        long clamped = clamp(storedEnergy);
        if (this.storedEnergy != clamped) {
            this.storedEnergy = clamped;
            changeListener.run();
        } else {
            this.storedEnergy = clamped;
        }
    }

    public void loadStoredEnergy(long storedEnergy) {
        this.storedEnergy = clamp(storedEnergy);
    }

    @Override
    protected Long createSnapshot() {
        return storedEnergy;
    }

    @Override
    protected void revertToSnapshot(Long snapshot) {
        storedEnergy = snapshot;
    }

    @Override
    protected void onRootCommit(Long originalState) {
        if (storedEnergy != originalState) {
            changeListener.run();
        }
    }

    private long clamp(long value) {
        return Math.max(0L, Math.min(value, capacity));
    }
}
