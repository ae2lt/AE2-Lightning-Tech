package com.moakiee.ae2lt.logic;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.neoforged.fml.ModList;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.api.crafting.PatternDetailsHelper;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import net.pedroksl.advanced_ae.common.patterns.IAdvPatternDetails;
import net.pedroksl.advanced_ae.common.patterns.AdvPatternDetailsEncoder;
import net.pedroksl.advanced_ae.common.patterns.AdvProcessingPattern;

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

    /** Converts a processing pattern to an AdvancedAE pattern with all inputs using any side. */
    @Nullable
    public static ItemStack encodeAnySide(ItemStack source, Level level) {
        return encodeWithDirections(source, level, List.of());
    }

    /** Icon stack for UI entry points; empty when AdvancedAE is absent. */
    public static ItemStack advancedPatternIcon() {
        if (!isLoaded()) return ItemStack.EMPTY;
        return net.pedroksl.advanced_ae.common.definitions.AAEItems.ADV_PROCESSING_PATTERN.stack();
    }

    /**
     * Converts a processing pattern while assigning a side to each sparse input.
     * Values use {@code 0 = any side} and {@code Direction.ordinal() + 1}.
     */
    @Nullable
    public static ItemStack encodeWithDirections(ItemStack source, Level level, List<Integer> configuredSides) {
        if (!isLoaded() || source == null || source.isEmpty() || level == null) return null;
        var details = PatternDetailsHelper.decodePattern(source, level);
        List<GenericStack> inputs;
        List<GenericStack> outputs;
        if (details instanceof AEProcessingPattern processing) {
            inputs = processing.getSparseInputs();
            outputs = processing.getSparseOutputs();
        } else if (details instanceof AdvProcessingPattern advanced) {
            inputs = advanced.getSparseInputs();
            outputs = advanced.getSparseOutputs();
        } else return null;
        var directions = new LinkedHashMap<AEKey, Direction>();
        for (int i = 0; i < inputs.size(); i++) {
            var input = inputs.get(i);
            if (input == null) continue;
            int encoded = i < configuredSides.size() ? configuredSides.get(i) : 0;
            Direction direction = encoded > 0 && encoded <= Direction.values().length
                    ? Direction.values()[encoded - 1] : null;
            directions.putIfAbsent(input.what(), direction);
        }
        return AdvPatternDetailsEncoder.encodeProcessingPattern(
                inputs, outputs, directions);
    }

    private AdvancedAECompat() {}
}
