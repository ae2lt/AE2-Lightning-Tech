package com.moakiee.ae2lt.logic.batch;

import java.util.Iterator;
import java.util.Map;

import net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKeyType;
import appeng.crafting.inv.ListCraftingInventory;

import com.moakiee.ae2lt.mixin.AaeElapsedTimeTrackerAccessor;
import com.moakiee.ae2lt.mixin.AaeExecutingCraftingJobAccessor;
import com.moakiee.ae2lt.mixin.AaeTaskProgressAccessor;

public final class AaeBatchJobView implements BatchJobView, BatchTaskHandle, Iterator<BatchTaskHandle> {
    private final ExecutingCraftingJob job;
    private Iterator<? extends Map.Entry<IPatternDetails, ?>> rawIter;
    private Map.Entry<IPatternDetails, ?> currentEntry;

    public AaeBatchJobView(ExecutingCraftingJob job) {
        this.job = job;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Iterator<BatchTaskHandle> taskIterator() {
        Map<IPatternDetails, ?> tasks = ((AaeExecutingCraftingJobAccessor) (Object) job).getTasks();
        rawIter = (Iterator<? extends Map.Entry<IPatternDetails, ?>>) tasks.entrySet().iterator();
        currentEntry = null;
        return this;
    }

    @Override
    public boolean hasNext() {
        return rawIter.hasNext();
    }

    @Override
    public BatchTaskHandle next() {
        currentEntry = rawIter.next();
        return this;
    }

    @Override
    public void remove() {
        rawIter.remove();
        currentEntry = null;
    }

    @Override
    public IPatternDetails details() {
        return currentEntry.getKey();
    }

    @Override
    public long getValue() {
        return ((AaeTaskProgressAccessor) currentEntry.getValue()).getValue();
    }

    @Override
    public void setValue(long value) {
        ((AaeTaskProgressAccessor) currentEntry.getValue()).setValue(value);
    }

    @Override
    public ListCraftingInventory waitingFor() {
        return ((AaeExecutingCraftingJobAccessor) (Object) job).getWaitingFor();
    }

    @Override
    public void addContainerMaxItems(long count, AEKeyType type) {
        var tracker = ((AaeExecutingCraftingJobAccessor) (Object) job).getTimeTracker();
        ((AaeElapsedTimeTrackerAccessor) tracker).invokeAddMaxItems(count, type);
    }
}
