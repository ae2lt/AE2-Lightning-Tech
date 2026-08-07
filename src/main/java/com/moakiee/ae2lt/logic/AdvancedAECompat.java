package com.moakiee.ae2lt.logic;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.neoforged.fml.ModList;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.api.crafting.PatternDetailsHelper;
import com.moakiee.ae2lt.overload.runtime.model.MatchMode;
import com.moakiee.ae2lt.overload.runtime.pattern.OverloadedProviderOnlyPatternDetails;
import com.moakiee.thunderbolt.core.crafting.pattern.IWrappedPatternDetails;
import java.util.ArrayList;
import java.util.Collections;
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
        var unwrapped = unwrap(pattern);
        return isLoaded()
                && unwrapped instanceof IAdvPatternDetails adv
                && adv.directionalInputsSet();
    }

    /**
     * @return the target-machine face this key should be inserted into,
     *         or {@code null} for "use the default face".
     */
    @Nullable
    public static Direction getDirectionForKey(IPatternDetails pattern, AEKey key) {
        var unwrapped = unwrap(pattern);
        if (!(unwrapped instanceof IAdvPatternDetails adv)) {
            return null;
        }
        var direct = adv.getDirectionSideForInputKey(key);
        if (direct != null) {
            return direct;
        }

        // An overload pattern may accept an id-only variant whose component-bearing AEKey
        // differs from the key stored by AdvancedAE's direction map. Resolve that variant
        // through the overload slot and then query the original directional input key.
        if (pattern instanceof OverloadedProviderOnlyPatternDetails overload
                && key instanceof AEItemKey itemKey) {
            var sourceInputs = unwrapped.getInputs();
            for (var input : overload.overloadPatternDetailsView().inputs()) {
                if (input.matchMode() != MatchMode.ID_ONLY
                        || input.template().getItem() != itemKey.getItem()
                        || input.slotIndex() < 0
                        || input.slotIndex() >= sourceInputs.length) {
                    continue;
                }
                for (var possible : sourceInputs[input.slotIndex()].getPossibleInputs()) {
                    var direction = adv.getDirectionSideForInputKey(possible.what());
                    if (direction != null) {
                        return direction;
                    }
                }
            }
        }
        return null;
    }

    private static IPatternDetails unwrap(IPatternDetails pattern) {
        var current = pattern;
        for (int depth = 0; depth < 8 && current instanceof IWrappedPatternDetails wrapped; depth++) {
            var next = wrapped.wrappedPatternDetails();
            if (next == null || next == current) {
                break;
            }
            current = next;
        }
        return current;
    }

    /** Converts a processing pattern to an AdvancedAE pattern with all inputs using any side. */
    @Nullable
    public static ItemStack encodeAnySide(ItemStack source, Level level) {
        return encodeWithDirections(source, level, List.of());
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

    /**
     * Restores an AdvancedAE processing pattern into the terminal's sparse editing model.
     * Wrapped overload patterns are unwrapped first, so combined advanced-overload patterns
     * recover their directional layer as well.
     */
    @Nullable
    public static EditableProcessingPattern restoreForEditing(
            IPatternDetails pattern, int maxInputSlots, int maxOutputSlots) {
        if (!isLoaded() || pattern == null || maxInputSlots < 0 || maxOutputSlots < 0) {
            return null;
        }
        var unwrapped = unwrap(pattern);
        if (!(unwrapped instanceof AdvProcessingPattern advanced)) {
            return null;
        }
        var sparseInputs = advanced.getSparseInputs();
        var sparseOutputs = advanced.getSparseOutputs();
        if (sparseInputs.size() > maxInputSlots || sparseOutputs.size() > maxOutputSlots) {
            return null;
        }
        var inputs = nullableCopy(sparseInputs);
        var outputs = nullableCopy(sparseOutputs);
        var directions = new int[maxInputSlots];
        for (int slot = 0; slot < inputs.size(); slot++) {
            var input = inputs.get(slot);
            if (input == null || input.what() == null) continue;
            var direction = advanced.getDirectionSideForInputKey(input.what());
            directions[slot] = direction == null ? 0 : direction.ordinal() + 1;
        }
        return new EditableProcessingPattern(inputs, outputs, directions);
    }

    private static List<GenericStack> nullableCopy(List<GenericStack> source) {
        if (source == null || source.isEmpty()) return List.of();
        return Collections.unmodifiableList(new ArrayList<>(source));
    }

    public record EditableProcessingPattern(
            List<GenericStack> inputs, List<GenericStack> outputs, int[] directions) {
        public EditableProcessingPattern {
            inputs = Collections.unmodifiableList(new ArrayList<>(inputs));
            outputs = Collections.unmodifiableList(new ArrayList<>(outputs));
            directions = directions.clone();
        }

        @Override
        public int[] directions() {
            return directions.clone();
        }
    }

    private AdvancedAECompat() {}
}
