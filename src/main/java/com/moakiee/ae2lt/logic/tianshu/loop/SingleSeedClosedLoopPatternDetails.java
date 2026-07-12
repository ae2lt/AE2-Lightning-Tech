package com.moakiee.ae2lt.logic.tianshu.loop;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import com.moakiee.thunderbolt.ae2.batch.SharedBatchInputPattern;
import com.moakiee.thunderbolt.ae2.batch.BatchCopyLimitPattern;
import java.util.Set;

class SingleSeedClosedLoopPatternDetails extends ClosedLoopExpandedPatternDetails
        implements SharedBatchInputPattern, BatchCopyLimitPattern, ClosedLoopBatchPatternDetails {
    SingleSeedClosedLoopPatternDetails(IPatternDetails delegate,
                                       java.util.Map<AEKey, Long> seedAmounts,
                                       appeng.api.stacks.AEItemKey persistenceDefinition,
                                       int batchParallelism) {
        super(delegate, seedAmounts, persistenceDefinition, batchParallelism);
    }

    @Override
    public boolean isSharedBatchInput(int slot, AEKey concreteKey) {
        return isReusableSeedInput(slot, concreteKey);
    }

    @Override
    public long sharedBatchOutputAmount(AEKey outputKey) {
        return super.sharedBatchOutputAmount(outputKey);
    }

    @Override public int maxBatchCopies() { return batchParallelism; }
}
