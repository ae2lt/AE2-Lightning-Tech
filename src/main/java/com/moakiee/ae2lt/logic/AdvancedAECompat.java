package com.moakiee.ae2lt.logic;

import java.lang.reflect.Method;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraftforge.fml.ModList;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;

import com.moakiee.ae2lt.util.MixinReflectionSupport;

/**
 * Runtime compatibility layer for AdvancedAE directional processing patterns.
 * All references to AdvancedAE classes are confined to this file so that the
 * rest of the codebase never triggers {@link ClassNotFoundException} when
 * AdvancedAE is absent.
 */
public final class AdvancedAECompat {

    private static final @Nullable Class<?> ADV_PATTERN_DETAILS_CLASS =
            MixinReflectionSupport.findClassSafe("net.pedroksl.advanced_ae.common.patterns.AdvPatternDetails");
    private static final @Nullable Method DIRECTIONAL_INPUTS_SET_METHOD =
            MixinReflectionSupport.findDeclaredMethodSafe(ADV_PATTERN_DETAILS_CLASS, "directionalInputsSet");
    private static final @Nullable Method GET_DIRECTION_SIDE_FOR_INPUT_KEY_METHOD =
            MixinReflectionSupport.findDeclaredMethodSafe(
                    ADV_PATTERN_DETAILS_CLASS,
                    "getDirectionSideForInputKey",
                    AEKey.class);

    private static Boolean loaded;

    public static boolean isLoaded() {
        if (loaded == null) {
            loaded = ModList.get().isLoaded("advanced_ae")
                    && ADV_PATTERN_DETAILS_CLASS != null
                    && DIRECTIONAL_INPUTS_SET_METHOD != null
                    && GET_DIRECTION_SIDE_FOR_INPUT_KEY_METHOD != null;
        }
        return loaded;
    }

    /**
     * @return {@code true} if AdvancedAE is present and the pattern carries
     *         a non-empty direction map.
     */
    public static boolean isDirectional(IPatternDetails pattern) {
        Object advPattern = asAdvPatternDetails(pattern);
        if (!isLoaded() || advPattern == null) {
            return false;
        }

        Object result = MixinReflectionSupport.invokeMethodSafe(
                DIRECTIONAL_INPUTS_SET_METHOD,
                advPattern,
                "read AdvancedAE directional inputs");
        return result instanceof Boolean directional && directional;
    }

    /**
     * @return the target-machine face this key should be inserted into,
     *         or {@code null} for "use the default face".
     */
    @Nullable
    public static Direction getDirectionForKey(IPatternDetails pattern, AEKey key) {
        Object advPattern = asAdvPatternDetails(pattern);
        if (advPattern == null) {
            return null;
        }

        Object result = MixinReflectionSupport.invokeMethodSafe(
                GET_DIRECTION_SIDE_FOR_INPUT_KEY_METHOD,
                advPattern,
                "read AdvancedAE input direction",
                key);
        return result instanceof Direction direction ? direction : null;
    }

    private AdvancedAECompat() {}

    @Nullable
    private static Object asAdvPatternDetails(IPatternDetails pattern) {
        return isLoaded() && ADV_PATTERN_DETAILS_CLASS != null && ADV_PATTERN_DETAILS_CLASS.isInstance(pattern)
                ? pattern
                : null;
    }
}

