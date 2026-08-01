package com.moakiee.ae2lt.logic;

import java.util.Objects;

/**
 * Target/pattern scheduling key backed by the provider's safe pattern key.
 *
 * <p>It never stores or compares third-party pattern details directly.</p>
 */
final class TargetPatternKey<T> {
    private final T target;
    private final ProviderPatternKey pattern;
    private final int hashCode;

    TargetPatternKey(T target, ProviderPatternKey pattern) {
        this.target = Objects.requireNonNull(target, "target");
        this.pattern = pattern;
        this.hashCode = 31 * target.hashCode()
                + Objects.hashCode(pattern);
    }

    T target() {
        return target;
    }

    ProviderPatternKey pattern() {
        return pattern;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof TargetPatternKey<?> key
                && target.equals(key.target)
                && Objects.equals(pattern, key.pattern);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }
}
