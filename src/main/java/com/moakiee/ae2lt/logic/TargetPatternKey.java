package com.moakiee.ae2lt.logic;

import java.util.Objects;

import appeng.api.crafting.IPatternDetails;

/**
 * Target/pattern scheduling key whose pattern half uses canonical identity.
 *
 * <p>It never invokes third-party pattern equality or hashing.</p>
 */
final class TargetPatternKey<T> {
    private final T target;
    private final IPatternDetails pattern;
    private final int hashCode;

    TargetPatternKey(T target, IPatternDetails pattern) {
        this.target = Objects.requireNonNull(target, "target");
        this.pattern = pattern;
        this.hashCode = 31 * target.hashCode()
                + System.identityHashCode(pattern);
    }

    T target() {
        return target;
    }

    IPatternDetails pattern() {
        return pattern;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof TargetPatternKey<?> key
                && target.equals(key.target)
                && pattern == key.pattern;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }
}
