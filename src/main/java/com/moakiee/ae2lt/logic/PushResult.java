package com.moakiee.ae2lt.logic;

import java.util.List;

import appeng.api.stacks.GenericStack;

/**
 * Result of a {@link MachineAdapter#pushCopies} call.
 *
 * @param acceptedCopies number of pattern copies whose ownership left the CPU
 *                       (0 .. maxCopies). A generic target that accepted part of
 *                       an aggregate transfers ownership of the whole aggregate:
 *                       the provider retains the remainder in {@code overflow}.
 * @param overflow       owned inputs that were committed but could not be fully
 *                       inserted; must be retried via
 *                       {@link MachineAdapter#flushOverflow}
 */
public record PushResult(int acceptedCopies, List<GenericStack> overflow) {

    /** Convenience constant for "nothing was accepted". */
    public static final PushResult REJECTED = new PushResult(0, List.of());
}
