package com.moakiee.ae2lt.logic.tianshu.maintenance;

/**
 * Smooths a complete rule scan over a fixed number of server ticks.
 *
 * <p>The accumulated-credit form keeps the total work proportional to the
 * configured rule count: one rule produces one check per period, while the
 * maximum 2048 rules produce exactly 32 checks per tick for a 64-tick period.</p>
 */
final class FixedPeriodPollingSchedule {
    private final int period;
    private long credit;
    private int cursor;

    FixedPeriodPollingSchedule(int period) {
        if (period <= 0) {
            throw new IllegalArgumentException("period must be positive");
        }
        this.period = period;
    }

    int checksThisTick(int ruleCount) {
        if (ruleCount <= 0) {
            return 0;
        }
        credit += ruleCount;
        int checks = (int) (credit / period);
        credit %= period;
        return checks;
    }

    int nextIndex(int ruleCount) {
        if (ruleCount <= 0) {
            throw new IllegalArgumentException("ruleCount must be positive");
        }
        if (cursor >= ruleCount) {
            cursor = 0;
        }
        int result = cursor++;
        if (cursor >= ruleCount) {
            cursor = 0;
        }
        return result;
    }

    void reset() {
        credit = 0L;
        cursor = 0;
    }
}
