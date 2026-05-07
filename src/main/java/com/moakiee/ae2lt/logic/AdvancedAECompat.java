package com.moakiee.ae2lt.logic;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraftforge.fml.ModList;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;

import net.pedroksl.advanced_ae.common.patterns.IAdvPatternDetails;

/**
 * Runtime compatibility layer for AdvancedAE directional processing patterns.
 * All references to AdvancedAE classes are confined to this file so that the
 * rest of the codebase never triggers {@link ClassNotFoundException} when
 * AdvancedAE is absent.
 */
public final class AdvancedAECompat {

    private static Boolean loaded;

    public static boolean isLoaded() {
        if (loaded == null) {
            loaded = ModList.get().isLoaded("advanced_ae");
        }
        return loaded;
    }

    /**
     * @return {@code true} if AdvancedAE is present and the pattern carries
     *         a non-empty direction map.
     */
    public static boolean isDirectional(IPatternDetails pattern) {
        return isLoaded()
                && pattern instanceof IAdvPatternDetails adv
                && adv.directionalInputsSet();
    }

    /**
     * @return the target-machine face this key should be inserted into,
     *         or {@code null} for "use the default face".
     */
    @Nullable
    public static Direction getDirectionForKey(IPatternDetails pattern, AEKey key) {
        if (pattern instanceof IAdvPatternDetails adv) {
            return adv.getDirectionSideForInputKey(key);
        }
        return null;
    }

    private AdvancedAECompat() {}
}

