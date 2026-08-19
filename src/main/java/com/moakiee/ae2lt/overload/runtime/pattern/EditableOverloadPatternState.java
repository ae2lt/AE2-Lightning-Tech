package com.moakiee.ae2lt.overload.runtime.pattern;

import java.util.Objects;

import com.moakiee.ae2lt.overload.runtime.model.EncodedOverloadPattern;

/**
 * Restored editing state for an existing overload pattern item.
 */
public record EditableOverloadPatternState(
        ParsedPatternDefinition parsedPattern,
        EncodedOverloadPattern encodedPattern
) {
    public EditableOverloadPatternState {
        Objects.requireNonNull(parsedPattern, "parsedPattern");
        Objects.requireNonNull(encodedPattern, "encodedPattern");
    }
}
