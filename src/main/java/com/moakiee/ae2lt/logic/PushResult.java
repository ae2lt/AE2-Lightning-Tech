package com.moakiee.ae2lt.logic;

import java.util.List;

import appeng.api.stacks.GenericStack;

/**
 * Result of a {@link MachineAdapter#pushCopies} call.
 *
 * @param acceptedCopies number of pattern copies the machine actually consumed (0 .. maxCopies)
 * @param overflow       items that were committed but could not be fully inserted;
 *                       must be retried via {@link MachineAdapter#flushOverflow}
 */
public record PushResult(int acceptedCopies, List<GenericStack> overflow) {

    /** Convenience constant for "nothing was accepted". */
    public static final PushResult REJECTED = new PushResult(0, List.of());
}
