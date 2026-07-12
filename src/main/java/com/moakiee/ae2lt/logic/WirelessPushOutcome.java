package com.moakiee.ae2lt.logic;

enum WirelessPushOutcome {
    SUCCESS,
    SOFT_FAIL,
    HARD_FAIL,
    GLOBAL_ABORT;

    boolean consumesTargetAttempt() {
        return this != GLOBAL_ABORT;
    }
}
