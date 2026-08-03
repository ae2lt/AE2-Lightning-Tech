package com.moakiee.ae2lt.logic;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEKey;
import appeng.util.inv.AppEngInternalInventory;

import com.moakiee.thunderbolt.ae2.api.crafting.CraftingPatternDelegates;

/**
 * Current decoded-pattern catalog for one overloaded pattern provider.
 *
 * <p>The catalog deliberately has the same lifetime as the provider's visible
 * pattern list. It gives every registered recipe stable provider details while
 * retaining the caller's execution details for fuzzy inputs and loop accounting.
 * It is not a global interner: decoded details may depend on the current level
 * and recipe reload state.
 */
final class OverloadedProviderPatternCatalog {

    /**
     * Fast path from each observed execution object to the provider-owned
     * details used by dispatch bookkeeping. Weak identity keys avoid retaining
     * transient CPU wrappers and never invoke their equality implementation.
     */
    private final Map<IPatternDetails, IPatternDetails> resolvedByIdentity =
            WeakIdentityMaps.weakKeysAndValues();

    /**
     * Cold-path semantic index of the patterns currently registered by this
     * provider. A newly decoded/equivalent IPD pays its normal equality cost
     * once while its registered representative remains live, then enters
     * {@link #resolvedByIdentity}.
     */
    private final Map<IPatternDetails, Registration> registeredByEquality =
            new WeakHashMap<>();

    /** Stable inventory-slot bridge used only for save/load bookkeeping. */
    private final Map<IPatternDetails, Integer> slotByCanonicalIdentity =
            new IdentityHashMap<>();
    private final Map<Integer, IPatternDetails> canonicalBySlot =
            new HashMap<>();

    void rebuild(
            AppEngInternalInventory inventory,
            Level level,
            List<IPatternDetails> visiblePatterns,
            Set<AEKey> patternInputs) {
        var previousRegistrations = new HashMap<>(registeredByEquality);
        clear();
        visiblePatterns.clear();
        patternInputs.clear();

        for (int slot = 0; slot < inventory.size(); slot++) {
            var details = PatternDetailsHelper.decodePattern(
                    inventory.getStackInSlot(slot), level);
            if (details == null) {
                continue;
            }

            visiblePatterns.add(details);
            register(details, previousRegistrations, slot);
            for (var input : details.getInputs()) {
                for (var possibleInput : input.getPossibleInputs()) {
                    patternInputs.add(possibleInput.what().dropSecondary());
                }
            }
        }
    }

    @Nullable
    IPatternDetails resolve(IPatternDetails executionDetails) {
        if (executionDetails == null) {
            return null;
        }

        var direct = resolvedByIdentity.get(executionDetails);
        if (direct != null) {
            return direct;
        }

        var providerDetails = CraftingPatternDelegates.forProviderLookup(executionDetails);
        if (providerDetails != executionDetails) {
            direct = resolvedByIdentity.get(providerDetails);
            if (direct != null) {
                resolvedByIdentity.put(executionDetails, direct);
                return direct;
            }
        }

        // This is the only IPatternDetails equality lookup on the resolve path.
        // It preserves AE2's provider-map semantics for re-decoded details and
        // third-party implementations instead of assuming definition equality
        // is universally interchangeable with pattern equality.
        var registration = registeredByEquality.get(providerDetails);
        var resolved = registration == null ? null : registration.resolve();
        if (resolved != null) {
            resolvedByIdentity.put(providerDetails, resolved);
            if (providerDetails != executionDetails) {
                resolvedByIdentity.put(executionDetails, resolved);
            }
        }
        return resolved;
    }

    void clear() {
        resolvedByIdentity.clear();
        registeredByEquality.clear();
        slotByCanonicalIdentity.clear();
        canonicalBySlot.clear();
    }

    void register(IPatternDetails details) {
        register(details, Map.of(), -1);
    }

    void register(IPatternDetails details, int slot) {
        register(details, Map.of(), slot);
    }

    private void register(
            IPatternDetails details,
            Map<IPatternDetails, Registration> previousRegistrations,
            int slot) {
        // Preserve the provider-owned representative across an equivalent
        // decode/rebuild only while another owner still retains it. This
        // comparison is off the dispatch hot path.
        var registration = registeredByEquality.get(details);
        if (registration == null) {
            var previous = previousRegistrations.get(details);
            var previousDetails = previous == null ? null : previous.resolve();
            var candidate = previousDetails != null ? previousDetails : details;
            registration = new Registration(candidate, details);
            registeredByEquality.put(details, registration);
        }
        var canonicalDetails = registration.resolve();
        if (canonicalDetails == null) {
            // The current decoded details are strongly owned by AE2's visible
            // pattern list, so this is only a defensive fallback for unusual
            // lifecycle ordering.
            canonicalDetails = details;
            registration = new Registration(details, details);
            registeredByEquality.put(details, registration);
        }
        resolvedByIdentity.put(details, canonicalDetails);
        if (slot >= 0) {
            canonicalBySlot.put(slot, canonicalDetails);
            slotByCanonicalIdentity.putIfAbsent(canonicalDetails, slot);
        }
    }

    int slotOf(IPatternDetails canonicalDetails) {
        return slotByCanonicalIdentity.getOrDefault(
                canonicalDetails, -1);
    }

    @Nullable
    IPatternDetails patternAtSlot(int slot) {
        return canonicalBySlot.get(slot);
    }

    /** A registration index entry that never owns either IPatternDetails object. */
    private static final class Registration {
        private final WeakReference<IPatternDetails> preferred;
        private final WeakReference<IPatternDetails> current;

        private Registration(
                IPatternDetails preferred,
                IPatternDetails current) {
            this.preferred = new WeakReference<>(preferred);
            this.current = new WeakReference<>(current);
        }

        @Nullable
        private IPatternDetails resolve() {
            var result = preferred.get();
            return result != null ? result : current.get();
        }
    }
}
