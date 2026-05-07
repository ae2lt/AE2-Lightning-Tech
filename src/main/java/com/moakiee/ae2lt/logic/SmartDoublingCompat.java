package com.moakiee.ae2lt.logic;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraftforge.fml.ModList;

import appeng.api.config.Setting;
import appeng.api.config.YesNo;
import appeng.api.crafting.IPatternDetails;
import appeng.helpers.patternprovider.PatternProviderLogic;

/**
 * Runtime compatibility shim for ExtendedAE_Plus's "Smart Doubling" feature.
 * <p>
 * Two EAP integration points need help here, both because the overload
 * pattern provider fully overrides vanilla code instead of delegating:
 * <ol>
 *   <li><b>Marker propagation.</b> EAP's
 *       {@code PatternProviderLogicDoublingMixin#eap$applySmartDoublingToPatterns}
 *       TAIL-injects {@code updatePatterns}; since our override doesn't call
 *       {@code super}, that TAIL never fires. {@link #applyTo} replays it.</li>
 *   <li><b>Scaled-pattern dispatch.</b> When smart doubling fires, the AE2
 *       crafting plan stores {@code ScaledProcessingPattern} instances and
 *       hands those (not the original) back to {@code pushPattern}. EAP
 *       {@code @Redirect}s the {@code patterns.contains(...)} call inside
 *       {@code PatternProviderLogic.pushPattern} to unwrap scaled patterns and
 *       match against {@code getOriginal()}. Our overrides bypass that
 *       redirect, so the contains check fails and dispatch silently aborts.
 *       {@link #containsOrUnwrapped} replays the same unwrap.</li>
 * </ol>
 * All references to EAP types are resolved reflectively so that ae2lt continues
 * to compile and load when EAP is absent.
 */
public final class SmartDoublingCompat {

    private static final Logger LOGGER = LoggerFactory.getLogger("ae2lt/SmartDoublingCompat");
    private static final String MOD_ID = "extendedae_plus";

    private record Handles(Class<?> awareClass, Setting<YesNo> setting, MethodHandle setAllowScaling) {}

    private record ScaledHandles(Class<?> scaledClass, MethodHandle getOriginal) {}

    private static volatile Handles HANDLES;
    private static volatile boolean INIT_DONE;

    private static volatile ScaledHandles SCALED_HANDLES;
    private static volatile boolean SCALED_INIT_DONE;

    private static Handles handles() {
        if (INIT_DONE) return HANDLES;
        synchronized (SmartDoublingCompat.class) {
            if (INIT_DONE) return HANDLES;
            try {
                if (!ModList.get().isLoaded(MOD_ID)) return null;
                Class<?> awareClass = Class.forName(
                        "com.extendedae_plus.api.smartDoubling.ISmartDoublingAwarePattern");
                Class<?> settingsClass = Class.forName(
                        "com.extendedae_plus.api.config.EAPSettings");
                Field f = settingsClass.getField("SMART_DOUBLING");
                @SuppressWarnings("unchecked")
                Setting<YesNo> setting = (Setting<YesNo>) f.get(null);
                MethodHandle setter = MethodHandles.publicLookup().findVirtual(
                        awareClass,
                        "eap$setAllowScaling",
                        MethodType.methodType(void.class, boolean.class));
                HANDLES = new Handles(awareClass, setting, setter);
                LOGGER.debug("[ae2lt] ExtendedAE_Plus smart-doubling compat wired.");
                return HANDLES;
            } catch (Throwable t) {
                LOGGER.warn("[ae2lt] Failed to wire ExtendedAE_Plus smart-doubling compat: {}",
                        t.toString());
                return null;
            } finally {
                INIT_DONE = true;
            }
        }
    }

    private static ScaledHandles scaledHandles() {
        if (SCALED_INIT_DONE) return SCALED_HANDLES;
        synchronized (SmartDoublingCompat.class) {
            if (SCALED_INIT_DONE) return SCALED_HANDLES;
            try {
                if (!ModList.get().isLoaded(MOD_ID)) return null;
                Class<?> scaledClass = Class.forName(
                        "com.extendedae_plus.api.crafting.ScaledProcessingPattern");
                MethodHandle getOriginal = MethodHandles.publicLookup().findVirtual(
                        scaledClass,
                        "getOriginal",
                        MethodType.methodType(IPatternDetails.class));
                SCALED_HANDLES = new ScaledHandles(scaledClass, getOriginal);
                return SCALED_HANDLES;
            } catch (Throwable t) {
                LOGGER.warn("[ae2lt] Failed to wire EAP ScaledProcessingPattern compat: {}",
                        t.toString());
                return null;
            } finally {
                SCALED_INIT_DONE = true;
            }
        }
    }

    /**
     * Mirror EAP's {@code PatternProviderLogicContainsRedirectMixin}: when
     * {@code pattern} is a {@code ScaledProcessingPattern}, accept the original
     * unwrapped instance as a match in {@code patterns}. Used by overrides of
     * {@code pushPattern} that don't delegate to {@code super} (and therefore
     * miss EAP's @Redirect on {@code PatternProviderLogic.pushPattern}).
     *
     * @return true if {@code pattern} (or its unwrapped original) is in {@code patterns}
     */
    public static boolean containsOrUnwrapped(List<IPatternDetails> patterns, IPatternDetails pattern) {
        if (patterns.contains(pattern)) return true;
        IPatternDetails unwrapped = unwrap(pattern);
        return unwrapped != null && patterns.contains(unwrapped);
    }

    /**
     * Returns the original pattern wrapped inside a
     * {@code ScaledProcessingPattern}, or {@code null} if {@code pattern} is
     * not scaled (or EAP is absent).
     */
    @Nullable
    public static IPatternDetails unwrap(IPatternDetails pattern) {
        ScaledHandles sh = scaledHandles();
        if (sh == null) return null;
        if (!sh.scaledClass.isInstance(pattern)) return null;
        try {
            return (IPatternDetails) sh.getOriginal.invoke(pattern);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Mirror EAP's
     * {@code PatternProviderLogicDoublingMixin#eap$applySmartDoublingToPatterns}
     * for an overload provider whose {@code updatePatterns} fully overrides the
     * vanilla implementation. Call at the end of the override after the
     * {@code patterns} list has been rebuilt.
     */
    public static void applyTo(PatternProviderLogic logic, List<IPatternDetails> patterns) {
        Handles h = handles();
        if (h == null) return;
        boolean allowScaling;
        try {
            allowScaling = logic.getConfigManager().getSetting(h.setting) == YesNo.YES;
        } catch (Throwable t) {
            // Setting not registered (EAP mixin failed to apply on this instance) -- silent no-op.
            return;
        }
        for (IPatternDetails details : patterns) {
            if (h.awareClass.isInstance(details)) {
                try {
                    h.setAllowScaling.invoke(details, allowScaling);
                } catch (Throwable t) {
                    LOGGER.debug("[ae2lt] eap$setAllowScaling invocation failed: {}", t.toString());
                }
            }
        }
    }

    private SmartDoublingCompat() {}
}

