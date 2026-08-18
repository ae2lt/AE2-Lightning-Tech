package com.moakiee.ae2lt.item;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class FrequencyCardCandidateSelector {
    private FrequencyCardCandidateSelector() {
    }

    public static <T> Selection<T> select(List<Candidate<T>> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Selection.empty();
        }

        var best = candidates.stream()
                .min(Comparator.comparingInt(candidate -> candidate.source().priority()));
        if (best.isEmpty()) {
            return Selection.empty();
        }

        int bestPriority = best.get().source().priority();
        long bestCount = candidates.stream()
                .filter(candidate -> candidate.source().priority() == bestPriority)
                .count();
        if (bestCount > 1) {
            return Selection.withAmbiguity();
        }
        return Selection.selected(best.get().value());
    }

    public enum Source {
        MAIN_HAND(0),
        OFF_HAND(1),
        CURIOS(2),
        HOTBAR(3),
        BACKPACK(4),
        WIRELESS_TERMINAL(5);

        private final int priority;

        Source(int priority) {
            this.priority = priority;
        }

        int priority() {
            return priority;
        }
    }

    public record Candidate<T>(Source source, T value) {
    }

    public record Selection<T>(Optional<T> selected, boolean ambiguous) {
        private static <T> Selection<T> empty() {
            return new Selection<>(Optional.empty(), false);
        }

        private static <T> Selection<T> withAmbiguity() {
            return new Selection<>(Optional.empty(), true);
        }

        private static <T> Selection<T> selected(T value) {
            return new Selection<>(Optional.of(value), false);
        }
    }
}
