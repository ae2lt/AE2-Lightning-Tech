package com.moakiee.ae2lt.overload.pattern;

/**
 * Declares which host execution path is allowed to run a pattern definition.
 * <p>
 * The first overload-pattern implementation is intentionally host-bound. It is
 * not a general extension of AE2's global pattern system.
 */
public enum PatternExecutionHostKind {
    OVERLOADED_PATTERN_PROVIDER
}
