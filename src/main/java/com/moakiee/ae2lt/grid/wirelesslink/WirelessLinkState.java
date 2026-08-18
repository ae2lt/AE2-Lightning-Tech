package com.moakiee.ae2lt.grid.wirelesslink;

public enum WirelessLinkState {
    CONNECTED(false, false),
    PENDING_TARGET_CHUNK(false, false),
    PENDING_TRANSMITTER(false, false),
    TARGET_NOT_READY(false, false),
    CLUSTER_FREQUENCY_CONFLICT(false, false),
    FREQUENCY_INVALID(false, false),
    PERMISSION_DENIED(false, false),
    TARGET_MISSING(true, true),
    TARGET_TYPE_CHANGED(true, true),
    TARGET_NOT_NETWORK_DEVICE(true, false),
    PART_MISSING(true, true),
    PART_TYPE_CHANGED(true, true),
    PART_NOT_NETWORK_DEVICE(true, false),
    REDUNDANT_LINK(true, false),
    DISCONNECTED(true, false),
    REMOVED(true, false);

    private final boolean cleanupCandidate;
    private final boolean deterministicFailure;

    WirelessLinkState(boolean cleanupCandidate, boolean deterministicFailure) {
        this.cleanupCandidate = cleanupCandidate;
        this.deterministicFailure = deterministicFailure;
    }

    public boolean isCleanupCandidate() {
        return cleanupCandidate;
    }

    /**
     * Whether this state means the bound target block (or part) is genuinely
     * gone or has been replaced by a different block — as opposed to a transient
     * condition (chunk not loaded, grid not booted, redundant/blocked link).
     *
     * <p>These are checked only after the target chunk is confirmed loaded, so
     * they are unambiguous: the player destroyed or replaced the device. Such
     * links are removed immediately to avoid leaking the link entry, its device
     * registration, and any runtime virtual connection, regardless of the
     * auto-cleanup config toggle.</p>
     */
    public boolean isDeterministicFailure() {
        return deterministicFailure;
    }
}
