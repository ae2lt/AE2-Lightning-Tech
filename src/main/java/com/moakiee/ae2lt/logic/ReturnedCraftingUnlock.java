package com.moakiee.ae2lt.logic;

import org.jetbrains.annotations.Nullable;

final class ReturnedCraftingUnlock {

    private ReturnedCraftingUnlock() {
    }

    static Result resolveMatchedAmount(boolean matches, long unlockAmount, long returnedAmount) {
        if (!matches || unlockAmount <= 0 || returnedAmount <= 0) {
            return Result.noMatch();
        }

        long remaining = unlockAmount - returnedAmount;
        return remaining <= 0 ? Result.resetLock() : Result.remaining(remaining);
    }

    record Result(boolean matched, @Nullable Long remainingAmount) {
        private static Result noMatch() {
            return new Result(false, null);
        }

        private static Result resetLock() {
            return new Result(true, null);
        }

        private static Result remaining(long remainingAmount) {
            return new Result(true, remainingAmount);
        }

        boolean shouldResetLock() {
            return matched && remainingAmount == null;
        }
    }
}
