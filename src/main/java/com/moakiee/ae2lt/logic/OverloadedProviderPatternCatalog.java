package com.moakiee.ae2lt.logic;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
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

    /** External details are indexed strictly by reference, never by mod-defined equality. */
    private final Map<IPatternDetails, IPatternDetails> byDetails =
            CanonicalPatternMaps.create();
    private final Map<AEItemKey, IPatternDetails> byDefinition =
            new HashMap<>();

    void rebuild(
            AppEngInternalInventory inventory,
            Level level,
            List<IPatternDetails> visiblePatterns,
            Set<AEKey> patternInputs) {
        var previousDefinitions = new HashMap<>(byDefinition);
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
            register(details, previousDefinitions);
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

        var direct = byDetails.get(executionDetails);
        if (direct != null) {
            return direct;
        }

        var providerDetails = CraftingPatternDelegates.forProviderLookup(executionDetails);
        if (providerDetails != executionDetails) {
            direct = byDetails.get(providerDetails);
            if (direct != null) {
                return direct;
            }
        }

        var definition = providerDetails.getDefinition();
        return definition == null ? null : byDefinition.get(definition);
    }

    void clear() {
        byDetails.clear();
        byDefinition.clear();
    }

    void register(IPatternDetails details) {
        register(details, Map.of());
    }

    private void register(
            IPatternDetails details,
            Map<AEItemKey, IPatternDetails> previousDefinitions) {
        var definition = details.getDefinition();
        var canonicalDetails = definition == null
                ? details
                : byDefinition.computeIfAbsent(
                        definition,
                        key -> previousDefinitions.getOrDefault(
                                key, details));
        byDetails.putIfAbsent(details, canonicalDetails);
    }
}
