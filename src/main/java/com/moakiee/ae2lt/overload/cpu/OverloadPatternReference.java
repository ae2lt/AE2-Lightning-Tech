package com.moakiee.ae2lt.overload.cpu;

import java.util.Objects;

import com.moakiee.ae2lt.overload.pattern.SourcePatternSnapshot;

/**
 * Stable reference to one overload pattern definition as seen by the CPU-side
 * overload tracking layer.
 * <p>
 * The exact fingerprinting strategy can be refined later. For now, the manager
 * only needs a stable per-pattern identity within one crafting job plus the
 * source plain-pattern snapshot for diagnostics and future persistence.
 */
public record OverloadPatternReference(
        String patternIdentity,
        SourcePatternSnapshot sourcePattern
) {
    public OverloadPatternReference {
        Objects.requireNonNull(patternIdentity, "patternIdentity");
        if (patternIdentity.isBlank()) {
            throw new IllegalArgumentException("patternIdentity must not be blank");
        }
        Objects.requireNonNull(sourcePattern, "sourcePattern");
    }
}
