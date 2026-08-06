package com.moakiee.ae2lt.logic;

import java.lang.reflect.Field;
import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraftforge.fml.ModList;

import appeng.api.config.Setting;
import appeng.api.config.YesNo;
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

    private static volatile Setting<YesNo> SETTING;
    private static volatile boolean INIT_DONE;

    private static Setting<YesNo> setting() {
        if (INIT_DONE) return SETTING;
        synchronized (AdvancedBlockingCompat.class) {
            if (INIT_DONE) return SETTING;
            try {
                if (!ModList.get().isLoaded(MOD_ID)) return null;
                Class<?> settingsClass = Class.forName(
                        "com.extendedae_plus.api.config.EAPSettings");
                Field f = settingsClass.getField("ADVANCED_BLOCKING");
                @SuppressWarnings("unchecked")
                Setting<YesNo> s = (Setting<YesNo>) f.get(null);
                SETTING = s;
                LOGGER.debug("[ae2lt] ExtendedAE_Plus advanced-blocking compat wired.");
                return SETTING;
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
     * @return {@code true} iff EAP's {@code ADVANCED_BLOCKING} is enabled on
     *         {@code logic} <em>and</em> {@code target} fully matches every
     *         input slot of {@code pattern}. When this returns {@code true},
     *         the caller should treat the push as not blocked even when vanilla
     *         blocking is on (mirrors EAP's
     *         {@code PatternProviderLogicAdvancedMixin}).
     */
    public static boolean shouldBypassBlocking(PatternProviderLogic logic,
                                               PatternProviderTarget target,
                                               IPatternDetails pattern) {
        Setting<YesNo> s = setting();
        if (s == null) return false;
        try {
            if (logic.getConfigManager().getSetting(s) != YesNo.YES) return false;
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

