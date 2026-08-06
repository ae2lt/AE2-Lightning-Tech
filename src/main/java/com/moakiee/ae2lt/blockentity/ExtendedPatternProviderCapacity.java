package com.moakiee.ae2lt.blockentity;

public final class ExtendedPatternProviderCapacity {
    public static final int DEFAULT_PAGES = 4;
    public static final int MAX_PAGES = 64;
    public static final int SLOTS_PER_PAGE = OverloadedPatternProviderBlockEntity.SLOTS_PER_PAGE;

    private ExtendedPatternProviderCapacity() {
    }

    public static int clampPages(int pages) {
        return Math.clamp(pages, 1, MAX_PAGES);
    }

    public static int slotsForPages(int pages) {
        return clampPages(pages) * SLOTS_PER_PAGE;
    }
}
