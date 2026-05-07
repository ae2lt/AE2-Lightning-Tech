package com.moakiee.ae2lt.overload.pattern;

import java.util.Objects;

import com.moakiee.ae2lt.overload.model.EncodedOverloadPattern;

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
