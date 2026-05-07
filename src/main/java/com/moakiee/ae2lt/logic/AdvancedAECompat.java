package com.moakiee.ae2lt.logic;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;

/**
 * Runtime compatibility layer for AdvancedAE directional processing patterns.
 * All references to AdvancedAE classes are confined to this file so that the
 * rest of the codebase never triggers {@link ClassNotFoundException} when
 * AdvancedAE is absent.
 */
public final class AdvancedAECompat {

    public static boolean isLoaded() {
        return false;
    }

    /**
     * @return {@code true} if AdvancedAE is present and the pattern carries
     *         a non-empty direction map.
     */
    public static boolean isDirectional(IPatternDetails pattern) {
        return false;
    }

    /**
     * @return the target-machine face this key should be inserted into,
     *         or {@code null} for "use the default face".
     */
    @Nullable
    public static Direction getDirectionForKey(IPatternDetails pattern, AEKey key) {
        return null;
    }

    private AdvancedAECompat() {}
}
