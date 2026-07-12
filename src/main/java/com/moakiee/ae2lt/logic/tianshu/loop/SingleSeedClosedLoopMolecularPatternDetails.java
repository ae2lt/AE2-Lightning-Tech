package com.moakiee.ae2lt.logic.tianshu.loop;

import appeng.api.stacks.AEKey;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import com.moakiee.thunderbolt.ae2.batch.SharedBatchInputPattern;
import com.moakiee.thunderbolt.ae2.batch.BatchCopyLimitPattern;
import java.util.Set;

final class SingleSeedClosedLoopMolecularPatternDetails extends ClosedLoopMolecularPatternDetails
        implements SharedBatchInputPattern, BatchCopyLimitPattern, ClosedLoopBatchPatternDetails {
    SingleSeedClosedLoopMolecularPatternDetails(
            IMolecularAssemblerSupportedPattern molecular, java.util.Map<AEKey, Long> seedAmounts,
            appeng.api.stacks.AEItemKey persistenceDefinition, int batchParallelism) {
        super(molecular, seedAmounts, persistenceDefinition, batchParallelism);
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
