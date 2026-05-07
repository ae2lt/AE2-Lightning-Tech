package com.moakiee.ae2lt.overload.pattern;

import java.util.Objects;

import com.moakiee.ae2lt.overload.model.EncodedOverloadPattern;

/**
 * Persistent payload carried by one overload pattern item.
 * <p>
 * The payload deliberately combines:
 * <ul>
 *   <li>the source plain-pattern snapshot, for future reparsing/editing</li>
 *   <li>the overload slot-mode configuration</li>
 *   <li>the required execution host identity</li>
 * </ul>
 */
public final class OverloadPatternPayload {
    private final PatternExecutionHostKind requiredHostKind;
    private final SourcePatternSnapshot sourcePattern;
    private final EncodedOverloadPattern encodedPattern;

    public OverloadPatternPayload(
            PatternExecutionHostKind requiredHostKind,
            SourcePatternSnapshot sourcePattern,
            EncodedOverloadPattern encodedPattern
    ) {
        this.requiredHostKind = Objects.requireNonNull(requiredHostKind, "requiredHostKind");
        this.sourcePattern = Objects.requireNonNull(sourcePattern, "sourcePattern");
        this.encodedPattern = Objects.requireNonNull(encodedPattern, "encodedPattern");
    }

    public PatternExecutionHostKind requiredHostKind() {
        return requiredHostKind;
    }

    public SourcePatternSnapshot sourcePattern() {
        return sourcePattern;
    }

    public EncodedOverloadPattern encodedPattern() {
        return encodedPattern;
    }
}
