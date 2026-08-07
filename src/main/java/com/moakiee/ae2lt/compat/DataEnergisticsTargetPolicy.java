package com.moakiee.ae2lt.compat;

import java.util.List;

/** Startup-safe target-name policy used before ordinary mod classes are available. */
public final class DataEnergisticsTargetPolicy {
    private static final String DATA_ENERGISTICS_MIXIN_PACKAGE =
            "com.fish_dan_.data_energistics.mixin.";
    private static final String LEGACY_AE2LT_PATHING_MIXIN =
            DATA_ENERGISTICS_MIXIN_PACKAGE + "ae2lt.Ae2ltPathingCalculationCompatMixin";
    private static final String OWNED_TARGET_PACKAGE = "com.moakiee.";

    private DataEnergisticsTargetPolicy() {}

    public static boolean shouldCancel(List<String> targetClassNames, String mixinClassName) {
        if (mixinClassName == null || !mixinClassName.startsWith(DATA_ENERGISTICS_MIXIN_PACKAGE)) {
            return false;
        }
        // This targets AE2's PathingCalculation, but shadows AE2LT's former
        // ae2lt$useMaxFlow implementation field. It must therefore be treated as an AE2LT-owned
        // compatibility surface even though the nominal target class is external.
        if (LEGACY_AE2LT_PATHING_MIXIN.equals(mixinClassName)) {
            return true;
        }
        if (targetClassNames == null) {
            return false;
        }
        for (String targetClassName : targetClassNames) {
            if (isOwnedTarget(targetClassName)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isOwnedTarget(String targetClassName) {
        if (targetClassName == null) {
            return false;
        }
        String normalized = targetClassName.replace('/', '.');
        if (normalized.startsWith("L") && normalized.endsWith(";")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return normalized.startsWith(OWNED_TARGET_PACKAGE);
    }
}
