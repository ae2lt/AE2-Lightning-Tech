package com.moakiee.ae2lt.crafting.timewheel;

/** Pure arithmetic used while one exact overload output is also present in AE2's waiting list. */
final class OverloadInsertAccounting {
    private OverloadInsertAccounting() {
    }

    /** Native exact demand beyond its mirrored ID_ONLY amount is genuine STRICT work. */
    static long strictPrefixBeforeExactOverload(
            long offered, long nativeExactWaiting, long exactOverloadWaiting) {
        long strict = Math.max(
                0L,
                Math.max(0L, nativeExactWaiting) - Math.max(0L, exactOverloadWaiting));
        return Math.min(Math.max(0L, offered), strict);
    }

    /** Restores the original offered amount for the non-mutating waiting-list probe. */
    static long strictProbeAmount(long remaining, long simulatedExactOverload) {
        long normalizedRemaining = Math.max(0L, remaining);
        long normalizedExact = Math.max(0L, simulatedExactOverload);
        return normalizedRemaining > Long.MAX_VALUE - normalizedExact
                ? Long.MAX_VALUE : normalizedRemaining + normalizedExact;
    }

    /** Removes the exact overload prefix already accepted from AE2's overlapping strict match. */
    static long strictMatchAfterExactOverload(
            long remaining,
            long rawStrictMatch,
            long simulatedExactOverload) {
        long withoutOverlap = Math.max(
                0L,
                Math.max(0L, rawStrictMatch) - Math.max(0L, simulatedExactOverload));
        return Math.min(Math.max(0L, remaining), withoutOverlap);
    }

    /** The same non-mutating overload entry must not be claimed a second time in this insert. */
    static boolean mayClaimOverloadRemainder(long simulatedExactOverload) {
        return simulatedExactOverload <= 0L;
    }

}
