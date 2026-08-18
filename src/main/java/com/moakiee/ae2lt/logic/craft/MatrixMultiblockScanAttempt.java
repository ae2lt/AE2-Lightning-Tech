package com.moakiee.ae2lt.logic.craft;

import java.util.List;

import net.minecraft.core.Direction;

public record MatrixMultiblockScanAttempt(
        Direction orientation,
        List<MatrixMultiblockScanIssue> issues,
        MatrixMultiblockScanResult result) {
    public MatrixMultiblockScanAttempt {
        issues = List.copyOf(issues);
    }

    public boolean formed() {
        return result != null && issues.isEmpty();
    }

    public boolean chunksUnavailable() {
        return issues.contains(MatrixMultiblockScanIssue.CHUNKS_UNLOADED);
    }
}
