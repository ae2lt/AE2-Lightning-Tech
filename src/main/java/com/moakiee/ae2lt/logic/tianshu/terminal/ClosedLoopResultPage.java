package com.moakiee.ae2lt.logic.tianshu.terminal;

import appeng.api.stacks.GenericStack;
import java.util.List;
import java.util.Objects;

/** A bounded, read-only page of computed closed-loop inputs shown by the detail screen. */
public record ClosedLoopResultPage(
        int revision,
        Kind kind,
        int offset,
        int total,
        List<GenericStack> entries) {
    public static final int MAX_RESULTS = 27 * 9;
    public static final int PAGE_SIZE = 5;

    public enum Kind {
        EXTERNAL_INPUTS,
        SEEDS
    }

    public ClosedLoopResultPage {
        kind = Objects.requireNonNull(kind, "kind");
        entries = entries == null ? List.of() : entries;
        if (offset < 0 || offset >= MAX_RESULTS) {
            throw new IllegalArgumentException("invalid closed-loop result offset: " + offset);
        }
        if (total < 0 || total > MAX_RESULTS) {
            throw new IllegalArgumentException("invalid closed-loop result count: " + total);
        }
        if ((total == 0 && offset != 0) || (total > 0 && offset >= total)) {
            throw new IllegalArgumentException(
                    "closed-loop result offset " + offset + " exceeds count " + total);
        }
        if (entries.size() > PAGE_SIZE || offset + entries.size() > total) {
            throw new IllegalArgumentException("invalid closed-loop result page size");
        }
        for (var entry : entries) {
            if (entry == null || entry.what() == null || entry.amount() <= 0L) {
                throw new IllegalArgumentException("invalid closed-loop result entry");
            }
        }
        entries = List.copyOf(entries);
    }

    public static ClosedLoopResultPage from(
            int revision, Kind kind, List<GenericStack> source, int requestedOffset) {
        source = source == null ? List.of() : source;
        int total = Math.min(MAX_RESULTS, source.size());
        int offset = total == 0 ? 0 : Math.max(0, Math.min(requestedOffset, total - 1));
        int end = Math.min(total, offset + PAGE_SIZE);
        return new ClosedLoopResultPage(
                revision, kind, offset, total, source.subList(offset, end));
    }
}
