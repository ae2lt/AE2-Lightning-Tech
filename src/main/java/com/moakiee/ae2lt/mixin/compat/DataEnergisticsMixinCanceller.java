package com.moakiee.ae2lt.mixin.compat;

import java.util.List;

import com.bawnorton.mixinsquared.api.MixinCanceller;
import com.moakiee.ae2lt.compat.DataEnergisticsTargetPolicy;
import com.moakiee.ae2lt.config.EarlyCompatibilityConfig;

/**
 * Prevents Data Energistics from mixing into classes owned by Moakiee projects.
 *
 * <p>Its injections into AE2, Minecraft and its other integrations remain its responsibility and
 * are deliberately left untouched. This boundary protects implementation classes under the
 * {@code com.moakiee} namespace, plus explicitly identified legacy compatibility Mixins that
 * shadow removed AE2LT implementation details.
 */
public final class DataEnergisticsMixinCanceller implements MixinCanceller {
    @Override
    public boolean shouldCancel(List<String> targetClassNames, String mixinClassName) {
        return EarlyCompatibilityConfig.dataEnergisticsMixinProtectionEnabled()
                && DataEnergisticsTargetPolicy.shouldCancel(targetClassNames, mixinClassName);
    }
}
