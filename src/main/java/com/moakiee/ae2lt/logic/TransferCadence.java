package com.moakiee.ae2lt.logic;

/**
 * Constant-size refill timing shared by pattern providers and independent-key
 * interface exports. Amounts are copies or items; no recipe, storage or map is
 * owned here. Physical insertion and ownership stay with the caller.
 */
public final class TransferCadence {
    public static final int MAX_COVERAGE_TICKS = 100;
    private static final int HISTORY_TTL = 100;
    private long lastSuccessTick = Long.MIN_VALUE;
    private long lastActivityTick = Long.MIN_VALUE;
    private long capacityEstimate;
    private long lastAmount;
    private int fullInterval = 1;
    private int fasterCandidate;
    private int rapidSamples;
    private int rejectedElapsed;
    private int nextInterval = 1;
    private boolean nextExploratory;

    /** requestLimited means the caller could not establish the target's full capacity. */
    public int success(long tick, long amount, boolean requestLimited) {
        if (amount <= 0) throw new IllegalArgumentException("A successful transfer must move something");
        expireIfIdle(tick);
        if (lastSuccessTick != Long.MIN_VALUE) {
            long elapsed = Math.max(1L, tick - lastSuccessTick);
            boolean capacityIncreased = amount > capacityEstimate;
            boolean drainIncreased = lastAmount > 0L
                    && amount > lastAmount
                    && amount - lastAmount
                            >= lastAmount / 2L + lastAmount % 2L;
            capacityEstimate = Math.max(
                    capacityEstimate, amount);
            if (capacityIncreased || drainIncreased) {
                rapidSamples = 4;
            }
            int candidate = estimateFullInterval(
                    capacityEstimate, elapsed, amount);
            // A fully accepted caller-limited refill is a censored rate sample:
            // the machine may have consumed more than this visit was allowed to send.
            if (requestLimited) {
                candidate = Math.min(candidate, (int) Math.min(MAX_COVERAGE_TICKS, elapsed));
            }
            if (rapidSamples > 0) {
                fullInterval = candidate;
                fasterCandidate = 0;
                rapidSamples--;
            } else if (candidate * 4L <= fullInterval * 3L) {
                if (fasterCandidate > 0
                        && candidate <= fasterCandidate * 2L
                        && fasterCandidate <= candidate * 2L) {
                    fullInterval = candidate;
                    fasterCandidate = 0;
                } else {
                    fasterCandidate = candidate;
                }
            } else {
                fullInterval = candidate;
                fasterCandidate = 0;
            }
        } else {
            capacityEstimate = amount;
            fullInterval = 1;
        }

        lastSuccessTick = tick;
        lastActivityTick = tick;
        lastAmount = amount;
        rejectedElapsed = 0;
        nextInterval = probeDelay(
                fullInterval, rapidSamples > 0);
        nextExploratory = nextInterval < fullInterval;
        return nextInterval;
    }

    /** Use only for an actual target rejection, never missing source stock or power. */
    public int blocked(long tick) {
        expireIfIdle(tick);
        lastActivityTick = tick;
        fasterCandidate = 0;
        nextExploratory = false;

        if (lastSuccessTick == Long.MIN_VALUE) {
            nextInterval = Math.min(
                    MAX_COVERAGE_TICKS,
                    Math.max(1, nextInterval * 2));
            return nextInterval;
        }

        int elapsed = (int) Math.max(1L, Math.min(MAX_COVERAGE_TICKS, tick - lastSuccessTick));
        rejectedElapsed = Math.max(rejectedElapsed, elapsed);
        if (rejectedElapsed >= fullInterval
                && fullInterval < MAX_COVERAGE_TICKS) {
            fullInterval = Math.min(
                    MAX_COVERAGE_TICKS,
                    Math.max(
                            rejectedElapsed + 1,
                            fullInterval * 2));
        }
        nextInterval = rejectedElapsed >= fullInterval
                ? fullInterval
                : fullInterval - elapsed;
        return nextInterval;
    }

    /** Source/budget starvation invalidates the elapsed drain sample, not the target. */
    public int unavailable(long tick, int maximumDelay) {
        expireIfIdle(tick);
        int delay = hasSuccess() ? 1 : Math.min(Math.max(1, maximumDelay),
                nextInterval + Math.max(1, maximumDelay / 10));
        reset();
        nextInterval = delay;
        lastActivityTick = tick;
        return delay;
    }

    public boolean isEarlyProbe() { return nextExploratory; }
    public boolean wasBlocked() { return rejectedElapsed > 0; }
    public boolean hasSuccess() { return lastSuccessTick != Long.MIN_VALUE; }

    public boolean expireIfIdle(long tick) {
        if (lastActivityTick != Long.MIN_VALUE
                && (tick < lastActivityTick || tick - lastActivityTick > HISTORY_TTL)) {
            reset();
            return true;
        }
        return false;
    }

    public void reset() {
        lastSuccessTick = lastActivityTick = Long.MIN_VALUE;
        capacityEstimate = lastAmount = 0;
        fullInterval = nextInterval = 1;
        fasterCandidate = rapidSamples = rejectedElapsed = 0;
        nextExploratory = false;
    }

    private static int estimateFullInterval(
            long capacity, long elapsed, long accepted) {
        long quotient;
        if (elapsed > Long.MAX_VALUE / capacity) {
            quotient = Long.MAX_VALUE;
        } else {
            long product = elapsed * capacity;
            quotient = product / accepted
                    + (product % accepted == 0L ? 0L : 1L);
        }
        return (int) Math.max(1L, Math.min(MAX_COVERAGE_TICKS, quotient));
    }

    private static int probeDelay(
            int fullInterval, boolean rapidLearning) {
        if (rapidLearning) {
            return Math.max(1, (fullInterval + 3) / 4);
        }
        return Math.max(1, (fullInterval + 1) / 2);
    }
}
