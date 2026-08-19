package com.moakiee.ae2lt.crafting.matrix.core;

import java.util.List;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;

public interface CopyAssembler {
    AssembledCopy assembleOneCopy(IPatternDetails details, KeyCounter[] oneCopyInputs);

    record AssembledCopy(AEKey output, long outputCount, List<Stack> remainders,
                         List<Stack> sharedRemainders) {
        public AssembledCopy(AEKey output, long outputCount, List<Stack> remainders) {
            this(output, outputCount, remainders, List.of());
        }
    }

    record Stack(AEKey key, long count) {
    }
}
