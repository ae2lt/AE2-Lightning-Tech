package com.moakiee.ae2lt.logic.tianshu.loop;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import com.moakiee.thunderbolt.ae2.batch.SharedBatchInputPattern;

class SingleSeedClosedLoopPatternDetails extends ClosedLoopExpandedPatternDetails
        implements SharedBatchInputPattern, ClosedLoopBatchPatternDetails {
    SingleSeedClosedLoopPatternDetails(IPatternDetails delegate,
                                       java.util.Map<AEKey, Long> seedAmounts,
                                       java.util.Set<AEKey> cycleKeys,
                                       java.util.UUID seedGroupId,
                                       boolean singleSeedInputPerMember,
                                       java.util.Map<Integer, AEKey> plannedSeedInputSlots,
                                       appeng.api.stacks.AEItemKey persistenceDefinition,
                                       int dispatchOrder) {
        super(delegate, seedAmounts, cycleKeys, seedGroupId, singleSeedInputPerMember,
                plannedSeedInputSlots, persistenceDefinition, dispatchOrder);
    }

    @Override
    public boolean isSharedBatchInput(int slot, AEKey concreteKey) {
        return isReusableSeedInput(slot, concreteKey);
    }

    @Override
    public long sharedBatchOutputAmount(AEKey outputKey) {
        return super.sharedBatchOutputAmount(outputKey);
    }
}
