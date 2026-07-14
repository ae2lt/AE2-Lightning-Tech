package com.moakiee.ae2lt.logic.tianshu.loop;

import appeng.api.stacks.AEKey;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import com.moakiee.thunderbolt.ae2.batch.SharedBatchInputPattern;

final class SingleSeedClosedLoopMolecularPatternDetails extends ClosedLoopMolecularPatternDetails
        implements SharedBatchInputPattern, ClosedLoopBatchPatternDetails {
    SingleSeedClosedLoopMolecularPatternDetails(
            IMolecularAssemblerSupportedPattern molecular, java.util.Map<AEKey, Long> seedAmounts,
            appeng.api.stacks.AEItemKey persistenceDefinition, int dispatchOrder) {
        super(molecular, seedAmounts, persistenceDefinition, dispatchOrder);
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
