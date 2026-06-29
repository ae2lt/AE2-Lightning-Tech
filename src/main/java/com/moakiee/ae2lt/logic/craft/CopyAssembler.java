package com.moakiee.ae2lt.logic.craft;

import java.util.List;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;

public interface CopyAssembler {
    AssembledCopy assembleOneCopy(IPatternDetails details, KeyCounter[] oneCopyInputs);

    record AssembledCopy(AEKey output, long outputCount, List<Stack> remainders) {
    }

    record Stack(AEKey key, long count) {
    }
}
