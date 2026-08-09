package com.moakiee.ae2lt.logic;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraftforge.fml.ModList;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderTarget;

/**
 * Runtime compatibility shim for ExtendedAE_Plus's "Advanced Blocking" feature.
 * <p>
 * EAP overrides vanilla blocking semantics by {@code @Redirect}-ing
 * {@code PatternProviderTarget.containsPatternInput(Set)} inside
 * {@code PatternProviderLogic#pushPattern}: when the target's contents fully
 * cover every input slot of the pattern, the push is allowed even with vanilla
 * blocking on. The overload provider's directional and wireless push paths do
 * not go through {@code super.pushPattern}, so the redirect never fires for
 * them. This shim lets those self-implemented paths reuse the same semantics.
 * <p>
 * EAP types are resolved reflectively for soft-dep safety.
 */
public final class AdvancedBlockingCompat {

    private static final Logger LOGGER = LoggerFactory.getLogger("ae2lt/AdvancedBlockingCompat");
    private static final String MOD_ID = "extendedae_plus";

    private record Handles(Class<?> advancedBlockingClass, MethodHandle getAdvancedBlocking) {}

    private static volatile Handles HANDLES;
    private static volatile boolean INIT_DONE;

    private static Handles handles() {
        if (INIT_DONE) return HANDLES;
        synchronized (AdvancedBlockingCompat.class) {
            if (INIT_DONE) return HANDLES;
            try {
                if (!ModList.get().isLoaded(MOD_ID)) return null;
                Class<?> advancedBlockingClass = Class.forName(
                        "com.extendedae_plus.api.advancedBlocking.IAdvancedBlocking");
                MethodHandle getter = MethodHandles.publicLookup().findVirtual(
                        advancedBlockingClass,
                        "eap$getAdvancedBlocking",
                        MethodType.methodType(boolean.class));
                HANDLES = new Handles(advancedBlockingClass, getter);
                LOGGER.debug("[ae2lt] ExtendedAE_Plus advanced-blocking compat wired.");
                return HANDLES;
            } catch (Throwable t) {
                LOGGER.warn("[ae2lt] Failed to wire ExtendedAE_Plus advanced-blocking compat: {}",
                        t.toString());
                return null;
            } finally {
                INIT_DONE = true;
            }
        }
    }

    /**
     * @return {@code true} iff EAP's per-provider advanced-blocking state is enabled on
     *         {@code logic} <em>and</em> {@code target} fully matches every
     *         input slot of {@code pattern}. When this returns {@code true},
     *         the caller should treat the push as not blocked even when vanilla
     *         blocking is on (mirrors EAP's
     *         {@code PatternProviderLogicAdvancedMixin}).
     */
    public static boolean shouldBypassBlocking(PatternProviderLogic logic,
                                               PatternProviderTarget target,
                                               IPatternDetails pattern) {
        Handles h = handles();
        if (h == null || !h.advancedBlockingClass.isInstance(logic)) return false;
        try {
            if (!(boolean) h.getAdvancedBlocking.invoke(logic)) return false;
        } catch (Throwable t) {
            return false;
        }
        return targetFullyMatchesPatternInputs(target, pattern);
    }

    /**
     * Direct port of EAP's {@code eap$targetFullyMatchesPatternInputs}: every
     * input slot must have at least one possible candidate already present in
     * the target.
     */
    private static boolean targetFullyMatchesPatternInputs(PatternProviderTarget target,
                                                           IPatternDetails pattern) {
        for (IPatternDetails.IInput in : pattern.getInputs()) {
            boolean slotMatched = false;
            for (var candidate : in.getPossibleInputs()) {
                AEKey key = candidate.what().dropSecondary();
                if (target.containsPatternInput(Collections.singleton(key))) {
                    slotMatched = true;
                    break;
                }
            }
            if (!slotMatched) return false;
        }
        return true;
    }

    private AdvancedBlockingCompat() {}
}
