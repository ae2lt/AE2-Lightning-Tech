package com.moakiee.ae2lt.blockentity;

import appeng.api.stacks.AEKey;
import com.moakiee.thunderbolt.ae2.timewheel.ReusableSeedPattern;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

final class ClosedLoopPublicationSupport {
    private ClosedLoopPublicationSupport() {
    }

    static <T> @Nullable T reusePublishedOrValidate(
            T candidate, Set<T> published, Supplier<T> validator) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(published, "published");
        Objects.requireNonNull(validator, "validator");
        return published.contains(candidate) ? candidate : validator.get();
    }

    /** Shares one immutable inventory view across every pattern in a single publication pass. */
    static final class SeedSnapshotMemoizer
            implements Function<ReusableSeedPattern, Map<AEKey, Long>> {
        private final Function<ReusableSeedPattern, Map<AEKey, Long>> source;
        private @Nullable Map<AEKey, Long> cached;

        SeedSnapshotMemoizer(Function<ReusableSeedPattern, Map<AEKey, Long>> source) {
            this.source = Objects.requireNonNull(source, "source");
        }

        @Override
        public Map<AEKey, Long> apply(ReusableSeedPattern pattern) {
            var result = cached;
            if (result == null) {
                result = Map.copyOf(Objects.requireNonNull(
                        source.apply(pattern), "seed snapshot source result"));
                cached = result;
            }
            return result;
        }
    }
}
