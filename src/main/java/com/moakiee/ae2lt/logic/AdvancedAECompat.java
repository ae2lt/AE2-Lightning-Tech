package com.moakiee.ae2lt.logic;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;

import com.moakiee.ae2lt.util.MixinReflectionSupport;
import com.moakiee.ae2lt.overload.runtime.model.MatchMode;
import com.moakiee.ae2lt.overload.runtime.pattern.OverloadedProviderOnlyPatternDetails;
import com.moakiee.thunderbolt.core.crafting.pattern.IWrappedPatternDetails;

/**
 * Runtime compatibility layer for AdvancedAE directional processing patterns.
 * All references to AdvancedAE classes are confined to this file so that the
 * rest of the codebase never triggers {@link ClassNotFoundException} when
 * AdvancedAE is absent. The supported Forge 1.20.1 release is optional, so
 * every AdvancedAE touchpoint goes through reflection and degrades to no-ops
 * when the mod is missing or its integration API is incompatible.
 */
public final class AdvancedAECompat {

    private static final @Nullable Class<?> ADV_PATTERN_DETAILS_CLASS =
            MixinReflectionSupport.findClassSafe("net.pedroksl.advanced_ae.common.patterns.AdvPatternDetails");
    private static final @Nullable Class<?> ADV_PROCESSING_PATTERN_CLASS =
            MixinReflectionSupport.findClassSafe("net.pedroksl.advanced_ae.common.patterns.AdvProcessingPattern");
    private static final @Nullable Class<?> ADV_ENCODER_CLASS =
            MixinReflectionSupport.findClassSafe("net.pedroksl.advanced_ae.common.patterns.AdvPatternDetailsEncoder");
    private static final @Nullable Method DIRECTIONAL_INPUTS_SET_METHOD =
            MixinReflectionSupport.findDeclaredMethodSafe(ADV_PATTERN_DETAILS_CLASS, "directionalInputsSet");
    private static final @Nullable Method GET_DIRECTION_SIDE_FOR_INPUT_KEY_METHOD =
            MixinReflectionSupport.findDeclaredMethodSafe(
                    ADV_PATTERN_DETAILS_CLASS,
                    "getDirectionSideForInputKey",
                    AEKey.class);
    private static final @Nullable Method ADV_GET_SPARSE_INPUTS_METHOD =
            findSparseAccessor(ADV_PROCESSING_PATTERN_CLASS, "getSparseInputs");
    private static final @Nullable Method ADV_GET_SPARSE_OUTPUTS_METHOD =
            findSparseAccessor(ADV_PROCESSING_PATTERN_CLASS, "getSparseOutputs");
    private static final @Nullable Method ADV_GET_DIRECTION_MAP_METHOD =
            findSparseAccessor(ADV_PROCESSING_PATTERN_CLASS, "getDirectionMap");
    private static final @Nullable Method ADV_ENCODE_METHOD = findEncodeMethod();

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
        var unwrapped = unwrap(pattern);
        Object advPattern = asAdvPatternDetails(unwrapped);
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
     * Compares the recipe-bearing fields of two directional processing patterns while ignoring
     * unrelated components added to the outer encoded-pattern item.
     */
    public static boolean samePatternSemantics(
            @Nullable IPatternDetails stored, @Nullable IPatternDetails candidate) {
        if (!isLoaded() || stored == null || candidate == null) return false;
        var storedUnwrapped = unwrap(stored);
        var candidateUnwrapped = unwrap(candidate);
        if (ADV_PROCESSING_PATTERN_CLASS == null
                || !ADV_PROCESSING_PATTERN_CLASS.isInstance(storedUnwrapped)
                || !ADV_PROCESSING_PATTERN_CLASS.isInstance(candidateUnwrapped)
                || ADV_GET_SPARSE_INPUTS_METHOD == null
                || ADV_GET_SPARSE_OUTPUTS_METHOD == null
                || ADV_GET_DIRECTION_MAP_METHOD == null) {
            return false;
        }
        var storedInputs = asStackList(MixinReflectionSupport.invokeMethodSafe(
                ADV_GET_SPARSE_INPUTS_METHOD, storedUnwrapped, "read AdvancedAE sparse inputs"));
        var candidateInputs = asStackList(MixinReflectionSupport.invokeMethodSafe(
                ADV_GET_SPARSE_INPUTS_METHOD, candidateUnwrapped, "read AdvancedAE sparse inputs"));
        var storedOutputs = asStackList(MixinReflectionSupport.invokeMethodSafe(
                ADV_GET_SPARSE_OUTPUTS_METHOD, storedUnwrapped, "read AdvancedAE sparse outputs"));
        var candidateOutputs = asStackList(MixinReflectionSupport.invokeMethodSafe(
                ADV_GET_SPARSE_OUTPUTS_METHOD, candidateUnwrapped, "read AdvancedAE sparse outputs"));
        var storedDirections = MixinReflectionSupport.invokeMethodSafe(
                ADV_GET_DIRECTION_MAP_METHOD, storedUnwrapped, "read AdvancedAE direction map");
        var candidateDirections = MixinReflectionSupport.invokeMethodSafe(
                ADV_GET_DIRECTION_MAP_METHOD, candidateUnwrapped, "read AdvancedAE direction map");
        return Objects.equals(storedInputs, candidateInputs)
                && Objects.equals(storedOutputs, candidateOutputs)
                && Objects.equals(storedDirections, candidateDirections);
    }

    /**
     * @return the target-machine face this key should be inserted into,
     *         or {@code null} for "use the default face".
     */
    @Nullable
    public static Direction getDirectionForKey(IPatternDetails pattern, AEKey key) {
        var unwrapped = unwrap(pattern);
        Object advPattern = asAdvPatternDetails(unwrapped);
        if (advPattern == null) {
            return null;
        }

        Object result = MixinReflectionSupport.invokeMethodSafe(
                GET_DIRECTION_SIDE_FOR_INPUT_KEY_METHOD,
                advPattern,
                "read AdvancedAE input direction",
                key);
        var direct = result instanceof Direction direction ? direction : null;
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
                    Object resolved = MixinReflectionSupport.invokeMethodSafe(
                            GET_DIRECTION_SIDE_FOR_INPUT_KEY_METHOD,
                            advPattern,
                            "read AdvancedAE input direction",
                            possible.what());
                    if (resolved instanceof Direction direction) {
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
        if (!isLoaded() || ADV_ENCODE_METHOD == null || source == null || source.isEmpty() || level == null) {
            return null;
        }
        var details = PatternDetailsHelper.decodePattern(source, level);
        List<GenericStack> inputs;
        List<GenericStack> outputs;
        if (details instanceof AEProcessingPattern processing) {
            inputs = Arrays.asList(processing.getSparseInputs());
            outputs = Arrays.asList(processing.getSparseOutputs());
        } else if (ADV_PROCESSING_PATTERN_CLASS != null
                && ADV_PROCESSING_PATTERN_CLASS.isInstance(details)
                && ADV_GET_SPARSE_INPUTS_METHOD != null
                && ADV_GET_SPARSE_OUTPUTS_METHOD != null) {
            Object sparseInputs = MixinReflectionSupport.invokeMethodSafe(
                    ADV_GET_SPARSE_INPUTS_METHOD, details, "read AdvancedAE sparse inputs");
            Object sparseOutputs = MixinReflectionSupport.invokeMethodSafe(
                    ADV_GET_SPARSE_OUTPUTS_METHOD, details, "read AdvancedAE sparse outputs");
            inputs = asStackList(sparseInputs);
            outputs = asStackList(sparseOutputs);
            if (inputs == null || outputs == null) return null;
        } else {
            return null;
        }
        var directions = new LinkedHashMap<AEKey, Direction>();
        for (int i = 0; i < inputs.size(); i++) {
            var input = inputs.get(i);
            if (input == null) continue;
            int encoded = i < configuredSides.size() ? configuredSides.get(i) : 0;
            Direction direction = encoded > 0 && encoded <= Direction.values().length
                    ? Direction.values()[encoded - 1] : null;
            directions.putIfAbsent(input.what(), direction);
        }
        Object encoded = MixinReflectionSupport.invokeMethodSafe(
                ADV_ENCODE_METHOD, null, "encode AdvancedAE processing pattern",
                inputs, outputs, directions);
        return encoded instanceof ItemStack stack ? stack : null;
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
        if (ADV_PROCESSING_PATTERN_CLASS == null
                || !ADV_PROCESSING_PATTERN_CLASS.isInstance(unwrapped)
                || ADV_GET_SPARSE_INPUTS_METHOD == null
                || ADV_GET_SPARSE_OUTPUTS_METHOD == null) {
            return null;
        }
        Object sparseInputs = MixinReflectionSupport.invokeMethodSafe(
                ADV_GET_SPARSE_INPUTS_METHOD, unwrapped, "read AdvancedAE sparse inputs");
        Object sparseOutputs = MixinReflectionSupport.invokeMethodSafe(
                ADV_GET_SPARSE_OUTPUTS_METHOD, unwrapped, "read AdvancedAE sparse outputs");
        var sparseInputList = asStackList(sparseInputs);
        var sparseOutputList = asStackList(sparseOutputs);
        if (sparseInputList == null || sparseOutputList == null) {
            return null;
        }
        if (sparseInputList.size() > maxInputSlots || sparseOutputList.size() > maxOutputSlots) {
            return null;
        }
        var inputs = nullableCopy(sparseInputList);
        var outputs = nullableCopy(sparseOutputList);
        var directions = new int[maxInputSlots];
        for (int slot = 0; slot < inputs.size(); slot++) {
            var input = inputs.get(slot);
            if (input == null || input.what() == null) continue;
            Object resolved = MixinReflectionSupport.invokeMethodSafe(
                    GET_DIRECTION_SIDE_FOR_INPUT_KEY_METHOD,
                    unwrapped,
                    "read AdvancedAE input direction",
                    input.what());
            directions[slot] = resolved instanceof Direction direction ? direction.ordinal() + 1 : 0;
        }
        return new EditableProcessingPattern(inputs, outputs, directions);
    }

    private static List<GenericStack> nullableCopy(List<GenericStack> source) {
        if (source == null || source.isEmpty()) return List.of();
        return Collections.unmodifiableList(new ArrayList<>(source));
    }

    @Nullable
    private static List<GenericStack> asStackList(@Nullable Object sparse) {
        if (sparse instanceof List<?> list) {
            var result = new ArrayList<GenericStack>(list.size());
            for (var element : list) {
                result.add(element instanceof GenericStack stack ? stack : null);
            }
            return result;
        }
        if (sparse instanceof GenericStack[] array) {
            return new ArrayList<>(Arrays.asList(array));
        }
        return null;
    }

    @Nullable
    private static Method findSparseAccessor(@Nullable Class<?> owner, String name) {
        if (owner == null) return null;
        try {
            var method = owner.getMethod(name);
            method.setAccessible(true);
            return method;
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    private static Method findEncodeMethod() {
        if (ADV_ENCODER_CLASS == null) return null;
        try {
            for (var method : ADV_ENCODER_CLASS.getDeclaredMethods()) {
                if (method.getName().equals("encodeProcessingPattern")
                        && Modifier.isStatic(method.getModifiers())
                        && method.getParameterCount() == 3) {
                    method.setAccessible(true);
                    return method;
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        return null;
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

    @Nullable
    private static Object asAdvPatternDetails(IPatternDetails pattern) {
        return isLoaded() && ADV_PATTERN_DETAILS_CLASS != null && ADV_PATTERN_DETAILS_CLASS.isInstance(pattern)
                ? pattern
                : null;
    }
}
