package com.moakiee.ae2lt.overload.runtime.pattern;

import appeng.api.crafting.IPatternDetails;

/**
 * Exposes the pattern details wrapped by an adapter without coupling consumers
 * to the adapter's concrete type.
 */
public interface WrappedPatternDetails
        extends com.moakiee.thunderbolt.core.crafting.support.IWrappedPatternDetails {
    @Override
    IPatternDetails wrappedPatternDetails();
}
