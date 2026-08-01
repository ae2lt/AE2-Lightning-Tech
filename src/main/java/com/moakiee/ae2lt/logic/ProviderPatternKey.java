package com.moakiee.ae2lt.logic;

import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;

/**
 * Provider-owned pattern identity that never delegates equality to pattern details.
 *
 * <p>Encoded patterns use their definition key, which remains stable when the
 * provider inventory is decoded again. Definition-less details fall back to
 * object identity; those cannot be reconstructed across a catalog rebuild.</p>
 */
final class ProviderPatternKey {
    @Nullable
    private final AEItemKey definition;
    @Nullable
    private final IPatternDetails identityFallback;
    private final int hashCode;

    private ProviderPatternKey(
            @Nullable AEItemKey definition,
            @Nullable IPatternDetails identityFallback) {
        this.definition = definition;
        this.identityFallback = identityFallback;
        this.hashCode = definition != null
                ? definition.hashCode()
                : System.identityHashCode(identityFallback);
    }

    static ProviderPatternKey forDefinition(AEItemKey definition) {
        return new ProviderPatternKey(
                Objects.requireNonNull(definition, "definition"), null);
    }

    static ProviderPatternKey forDetails(IPatternDetails details) {
        Objects.requireNonNull(details, "details");
        var definition = details.getDefinition();
        return definition != null
                ? forDefinition(definition)
                : new ProviderPatternKey(null, details);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProviderPatternKey key)) {
            return false;
        }
        if (definition != null || key.definition != null) {
            return definition != null
                    && key.definition != null
                    && definition.equals(key.definition);
        }
        return identityFallback == key.identityFallback;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }
}
