package com.moakiee.ae2lt.mixin.compat;

import java.util.List;

import com.bawnorton.mixinsquared.api.MixinCanceller;

/**
 * Cancels every mixin shipped by Data Energistics while AE2LT is present.
 *
 * <p>Data Energistics mixes into AE2 and AE2LT implementation details that AE2LT cannot provide
 * as a stable compatibility surface. Partially cancelling only the currently known collision
 * would leave the rest of that cross-mod patch set active and make failures version-dependent,
 * so this boundary deliberately disables the entire Data Energistics mixin package. The mod
 * itself is still loaded; features that depend on its mixins are unsupported by definition.
 */
public final class DataEnergisticsMixinCanceller implements MixinCanceller {
    static final String DATA_ENERGISTICS_MIXIN_PACKAGE =
            "com.fish_dan_.data_energistics.mixin.";

    @Override
    public boolean shouldCancel(List<String> targetClassNames, String mixinClassName) {
        return mixinClassName != null
                && mixinClassName.startsWith(DATA_ENERGISTICS_MIXIN_PACKAGE);
    }
}
