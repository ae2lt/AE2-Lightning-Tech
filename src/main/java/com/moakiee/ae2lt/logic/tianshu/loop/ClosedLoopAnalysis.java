package com.moakiee.ae2lt.logic.tianshu.loop;

import appeng.api.stacks.GenericStack;
import java.util.List;

public record ClosedLoopAnalysis(
        List<GenericStack> seeds,
        List<GenericStack> externalInputs,
        List<GenericStack> netOutputs) {
}
