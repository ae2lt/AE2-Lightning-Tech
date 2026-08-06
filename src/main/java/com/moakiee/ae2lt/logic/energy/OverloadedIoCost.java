package com.moakiee.ae2lt.logic.energy;

final class OverloadedIoCost {
    static final long ITEMS_PER_OPERATION = 4L;
    static final long FLUID_PER_OPERATION = 500L;

    private OverloadedIoCost() {
    }

    static double cost(long amount, long amountPerOperation) {
        if (amount <= 0) {
            return 0.0;
        }
        long perOperation = Math.max(1L, amountPerOperation);
        return Math.ceilDiv(amount, perOperation);
    }

    static long amountForOperations(long requested, long amountPerOperation, long operations) {
        if (requested <= 0 || operations <= 0) {
            return 0L;
        }
        long perOperation = Math.max(1L, amountPerOperation);
        long affordable;
        try {
            affordable = Math.multiplyExact(operations, perOperation);
        } catch (ArithmeticException overflow) {
            affordable = Long.MAX_VALUE;
        }
        return Math.min(requested, affordable);
    }
}
