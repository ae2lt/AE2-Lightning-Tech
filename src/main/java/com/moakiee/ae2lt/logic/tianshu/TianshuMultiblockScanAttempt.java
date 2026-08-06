package com.moakiee.ae2lt.logic.tianshu;

import java.util.List;

public record TianshuMultiblockScanAttempt(
        TianshuMultiblockScanResult result,
        List<TianshuMultiblockScanIssue> issues) {
    public boolean formed() {
        return result != null && issues.isEmpty();
    }

    public boolean chunksUnavailable() {
        return issues.contains(TianshuMultiblockScanIssue.CHUNKS_UNLOADED);
    }
}
