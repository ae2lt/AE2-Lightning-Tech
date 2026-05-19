package com.moakiee.ae2lt.overload.pattern;

import java.util.Objects;
import java.util.Optional;

import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.item.OverloadPatternItem;
import com.moakiee.ae2lt.overload.model.EncodedOverloadPattern;
import com.moakiee.ae2lt.overload.model.MatchMode;

/**
 * Converts plain-pattern parse results into overload-pattern payloads and item
 * stacks.
 * <p>
 * This service is intentionally limited to conversion and restoration. It does
 * not perform provider dispatch or CPU-side execution.
 */
public final class PatternConversionService {
    public OverloadPatternEditState createDefaultEditState(ParsedPatternDefinition parsedPattern) {
        return OverloadPatternEditState.fromPattern(parsedPattern, createDefaultEncoding(parsedPattern), false);
    }

    public OverloadPatternEditState createEditState(
            ParsedPatternDefinition parsedPattern,
            EncodedOverloadPattern encodedPattern,
            boolean sourceWasOverloadPattern
    ) {
        return OverloadPatternEditState.fromPattern(parsedPattern, encodedPattern, sourceWasOverloadPattern);
    }

    public EncodedOverloadPattern createDefaultEncoding(ParsedPatternDefinition parsedPattern) {
        Objects.requireNonNull(parsedPattern, "parsedPattern");

        var builder = EncodedOverloadPattern.builder();
        for (var input : parsedPattern.inputs()) {
            builder.input(input.slotIndex(), MatchMode.STRICT);
        }
        for (var output : parsedPattern.outputs()) {
            builder.output(output.slotIndex(), MatchMode.STRICT);
        }
        return builder.build();
    }

    public OverloadPatternPayload createPayload(ParsedPatternDefinition parsedPattern) {
        return createPayload(parsedPattern, createDefaultEncoding(parsedPattern));
    }

    public OverloadPatternPayload createPayload(
            ParsedPatternDefinition parsedPattern,
            EncodedOverloadPattern encodedPattern
    ) {
        Objects.requireNonNull(parsedPattern, "parsedPattern");
        Objects.requireNonNull(encodedPattern, "encodedPattern");

        return new OverloadPatternPayload(
                PatternExecutionHostKind.OVERLOADED_PATTERN_PROVIDER,
                parsedPattern.sourcePattern(),
                encodedPattern);
    }

    public ItemStack createOverloadPatternStack(
            OverloadPatternItem overloadPatternItem,
            ParsedPatternDefinition parsedPattern,
            EncodedOverloadPattern encodedPattern
    ) {
        Objects.requireNonNull(overloadPatternItem, "overloadPatternItem");
        var payload = createPayload(parsedPattern, encodedPattern);
        return overloadPatternItem.createStack(payload);
    }

    public ItemStack createOverloadPatternStack(
            OverloadPatternItem overloadPatternItem,
            ParsedPatternDefinition parsedPattern,
            OverloadPatternEditState editState
    ) {
        Objects.requireNonNull(editState, "editState");
        return createOverloadPatternStack(overloadPatternItem, parsedPattern, editState.toEncodedPattern());
    }

    public Optional<EditableOverloadPatternState> restoreEditableState(
            OverloadPatternItem overloadPatternItem,
            ItemStack overloadPatternStack,
            PlainPatternResolver plainPatternResolver
    ) {
        Objects.requireNonNull(overloadPatternItem, "overloadPatternItem");
        Objects.requireNonNull(overloadPatternStack, "overloadPatternStack");
        Objects.requireNonNull(plainPatternResolver, "plainPatternResolver");

        return overloadPatternItem.readPayload(overloadPatternStack)
                .map(payload -> {
                    var sourcePatternStack = payload.sourcePattern().toItemStack();
                    var parsedPattern = plainPatternResolver.resolve(sourcePatternStack);
                    return new EditableOverloadPatternState(parsedPattern, payload.encodedPattern());
                });
    }

    public Optional<EditableOverloadPatternState> resolveEditableSource(
            ItemStack sourcePatternStack,
            PlainPatternResolver plainPatternResolver
    ) {
        Objects.requireNonNull(sourcePatternStack, "sourcePatternStack");
        Objects.requireNonNull(plainPatternResolver, "plainPatternResolver");

        if (sourcePatternStack.isEmpty()) {
            return Optional.empty();
        }

        if (sourcePatternStack.getItem() instanceof OverloadPatternItem overloadPatternItem) {
            return restoreEditableState(overloadPatternItem, sourcePatternStack, plainPatternResolver);
        }

        var parsedPattern = plainPatternResolver.resolve(sourcePatternStack);
        return Optional.of(new EditableOverloadPatternState(parsedPattern, createDefaultEncoding(parsedPattern)));
    }

    public OverloadPatternDetails createRuntimeDetails(
            ParsedPatternDefinition parsedPattern,
            EncodedOverloadPattern encodedPattern
    ) {
        return new OverloadPatternDetails(parsedPattern, encodedPattern);
    }
}
