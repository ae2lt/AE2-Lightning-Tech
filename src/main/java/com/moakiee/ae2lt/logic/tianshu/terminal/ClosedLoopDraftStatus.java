package com.moakiee.ae2lt.logic.tianshu.terminal;

/** Server-authoritative state of the closed-loop authoring draft. */
public enum ClosedLoopDraftStatus {
    EMPTY,
    NO_CANDIDATE,
    MEMBER_UNDECODABLE,
    TOO_MANY_MEMBERS,
    NON_MINIMAL_COPIES,
    MISSING_PRIMARY_OUTPUT,
    INVALID_OUTPUT_MARKING,
    NOT_BALANCED,
    INVALID_SEED_ROUTING,
    VALID,
    ENCODED
}
