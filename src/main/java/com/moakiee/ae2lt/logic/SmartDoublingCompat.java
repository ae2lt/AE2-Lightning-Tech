package com.moakiee.ae2lt.logic;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraftforge.fml.ModList;

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

    private record Handles(
            Class<?> awareClass,
            Class<?> holderClass,
            Class<?> processingPatternClass,
            MethodHandle getSmartDoubling,
            MethodHandle getProviderLimit,
            MethodHandle setAllowScaling,
            MethodHandle setMultiplierLimit,
            MethodHandle computeMultiplier) {}

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
                Class<?> holderClass = Class.forName(
                        "com.extendedae_plus.api.smartDoubling.ISmartDoublingHolder");
                Class<?> processingPatternClass = Class.forName(
                        "appeng.crafting.pattern.AEProcessingPattern");
                Class<?> scalerClass = Class.forName(
                        "com.extendedae_plus.util.smartDoubling.PatternScaler");
                var lookup = MethodHandles.publicLookup();
                MethodHandle getSmartDoubling = lookup.findVirtual(
                        holderClass,
                        "eap$getSmartDoubling",
                        MethodType.methodType(boolean.class));
                MethodHandle getProviderLimit = lookup.findVirtual(
                        holderClass,
                        "eap$getProviderSmartDoublingLimit",
                        MethodType.methodType(int.class));
                MethodHandle setAllowScaling = lookup.findVirtual(
                        awareClass,
                        "eap$setAllowScaling",
                        MethodType.methodType(void.class, boolean.class));
                MethodHandle setMultiplierLimit = lookup.findVirtual(
                        awareClass,
                        "eap$setMultiplierLimit",
                        MethodType.methodType(void.class, int.class));
                MethodHandle computeMultiplier = lookup.findStatic(
                        scalerClass,
                        "getComputedMul",
                        MethodType.methodType(int.class, processingPatternClass, int.class));
                HANDLES = new Handles(
                        awareClass,
                        holderClass,
                        processingPatternClass,
                        getSmartDoubling,
                        getProviderLimit,
                        setAllowScaling,
                        setMultiplierLimit,
                        computeMultiplier);
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
                // Use unreflect so the handle keeps EAEP 1.20.1's concrete
                // AEProcessingPattern return descriptor. A lookup declared with
                // IPatternDetails does not match JVM method descriptors covariantly.
                MethodHandle getOriginal = MethodHandles.publicLookup().unreflect(
                        scaledClass.getMethod("getOriginal"));
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
        if (h == null || !h.holderClass.isInstance(logic)) return;
        boolean allowScaling;
        int providerLimit;
        try {
            allowScaling = (boolean) h.getSmartDoubling.invoke(logic);
            providerLimit = (int) h.getProviderLimit.invoke(logic);
        } catch (Throwable t) {
            return;
        }
        for (IPatternDetails details : patterns) {
            if (h.processingPatternClass.isInstance(details)
                    && h.awareClass.isInstance(details)) {
                try {
                    h.setAllowScaling.invoke(details, allowScaling);
                    int multiplierLimit = (int) h.computeMultiplier.invoke(
                            details, providerLimit);
                    h.setMultiplierLimit.invoke(details, multiplierLimit);
                } catch (Throwable t) {
                    LOGGER.debug("[ae2lt] Smart-doubling marker propagation failed: {}",
                            t.toString());
                }
            }
        }
    }

    private SmartDoublingCompat() {}
}
