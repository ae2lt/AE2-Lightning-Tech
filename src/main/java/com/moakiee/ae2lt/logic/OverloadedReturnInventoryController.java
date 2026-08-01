package com.moakiee.ae2lt.logic;

import java.util.Objects;

import appeng.api.storage.AEKeySlotFilter;
import appeng.api.stacks.GenericStack;

/** Owns the full return buffer and the nine-slot GUI page projected from it. */
final class OverloadedReturnInventoryController {
    private static final int PATTERNS_PER_PAGE = 36;
    private static final int RETURN_SLOTS_PER_PAGE = 9;

    private final UnlimitedReturnInventory full;
    private final UnlimitedReturnInventory pageView;
    private final GenericStack[] pageSnapshot =
            new GenericStack[RETURN_SLOTS_PER_PAGE];
    private final int totalPages;
    private boolean syncing;
    private int currentPage;

    OverloadedReturnInventoryController(
            int patternCapacity,
            Runnable changeListener,
            AEKeySlotFilter filter) {
        totalPages = Math.max(1,
                (patternCapacity + PATTERNS_PER_PAGE - 1) / PATTERNS_PER_PAGE);
        int fullSlots = totalPages * RETURN_SLOTS_PER_PAGE;
        full = fullSlots > RETURN_SLOTS_PER_PAGE
                ? UnlimitedReturnInventory.create(changeListener, filter, fullSlots)
                : UnlimitedReturnInventory.create(changeListener, filter);
        pageView = UnlimitedReturnInventory.create(() -> {
            if (!syncing) {
                copyPageToFull();
                changeListener.run();
            }
        }, filter);
    }

    UnlimitedReturnInventory full() {
        return full;
    }

    UnlimitedReturnInventory pageView() {
        return pageView;
    }

    int currentPage() {
        return currentPage;
    }

    int totalPages() {
        return totalPages;
    }

    void setCurrentPage(int page) {
        int bounded = Math.max(0, Math.min(page, totalPages - 1));
        if (bounded == currentPage) {
            return;
        }
        copyPageToFull();
        currentPage = bounded;
        copyFullToPage();
    }

    void copyFullToPage() {
        syncing = true;
        try {
            int offset = currentPage * RETURN_SLOTS_PER_PAGE;
            for (int i = 0; i < RETURN_SLOTS_PER_PAGE; i++) {
                int fullIndex = offset + i;
                var stack = fullIndex < full.size()
                        ? full.getStack(fullIndex)
                        : null;
                pageView.setStack(i, stack);
                pageSnapshot[i] = stack;
            }
        } finally {
            syncing = false;
        }
    }

    /** Copy only player-modified slots, preserving newer external inserts. */
    void copyPageToFull() {
        int offset = currentPage * RETURN_SLOTS_PER_PAGE;
        for (int i = 0; i < RETURN_SLOTS_PER_PAGE; i++) {
            int fullIndex = offset + i;
            if (fullIndex >= full.size()) {
                continue;
            }
            var current = pageView.getStack(i);
            if (Objects.equals(current, pageSnapshot[i])) {
                continue;
            }
            full.setStack(fullIndex, current);
            pageSnapshot[i] = current;
        }
    }
}
