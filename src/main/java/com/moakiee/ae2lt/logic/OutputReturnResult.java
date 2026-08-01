package com.moakiee.ae2lt.logic;

/** Outcome of one physical-machine output scan. */
public enum OutputReturnResult {
    /** At least one matching output was extracted. */
    EXTRACTED(true),
    /** Matching output exists, but the sink or machine rejected the transfer. */
    BLOCKED(true),
    /** The target was readable and contained no matching output. */
    EMPTY(false),
    /** The target could not expose a readable machine inventory. */
    UNAVAILABLE(false);

    private final boolean active;

    OutputReturnResult(boolean active) {
        this.active = active;
    }

    /** Whether periodic polling should remain at the active 20-tick interval. */
    boolean keepsSweepActive() {
        return active;
    }
}
