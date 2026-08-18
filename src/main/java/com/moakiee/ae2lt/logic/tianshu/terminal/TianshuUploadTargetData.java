package com.moakiee.ae2lt.logic.tianshu.terminal;

import appeng.api.implementations.blockentities.PatternContainerGroup;

/** Client-visible summary of one AE2 pattern-provider group. */
public record TianshuUploadTargetData(
        PatternContainerGroup group,
        int providerCount,
        int availableSlots) {
    public TianshuUploadTargetData {
        if (group == null) throw new IllegalArgumentException("group");
        providerCount = Math.max(0, providerCount);
        availableSlots = Math.max(0, availableSlots);
    }
}
