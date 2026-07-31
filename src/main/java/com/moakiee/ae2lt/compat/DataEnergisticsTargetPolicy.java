package com.moakiee.ae2lt.compat;

import java.util.List;

/** Startup-safe target-name policy used before ordinary mod classes are available. */
public final class DataEnergisticsTargetPolicy {
    private static final String DATA_ENERGISTICS_MIXIN_PACKAGE =
            "com.fish_dan_.data_energistics.mixin.";
    private static final String OWNED_TARGET_PACKAGE = "com.moakiee.";

    private DataEnergisticsTargetPolicy() {}

    public static boolean shouldCancel(List<String> targetClassNames, String mixinClassName) {
        if (mixinClassName == null || !mixinClassName.startsWith(DATA_ENERGISTICS_MIXIN_PACKAGE)) {
            return false;
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
