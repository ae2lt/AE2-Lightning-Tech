package com.moakiee.ae2lt.logic.tianshu.loop;

public record ClosedLoopValidationResult(
        Status status,
        ClosedLoopAnalysis analysis) {
    public boolean valid() {
        return status == Status.VALID;
    }

    public enum Status {
        VALID,
        MEMBER_UNDECODABLE,
        MEMBER_IS_CLOSED_LOOP,
        MEMBERS_NOT_CONNECTED,
        MEMBERS_NOT_MINIMAL,
        STRUCTURE_CHECK_FAILED,
        DECLARATION_MISMATCH,
        NO_VALID_NET_OUTPUT
    }
}
