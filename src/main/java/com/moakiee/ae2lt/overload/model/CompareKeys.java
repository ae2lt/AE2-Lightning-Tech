package com.moakiee.ae2lt.overload.model;

import java.util.Objects;

import net.minecraft.world.item.ItemStack;

/**
 * Small utility entry points for creating and evaluating overload compare keys.
 * <p>
 * Keep all overload-specific comparison helpers here so later execution code
 * does not need to know the concrete {@link CompareKey} implementation classes.
 */
public final class CompareKeys {
    private CompareKeys() {
    }

    public static CompareKey fromStack(ItemStack stack, MatchMode matchMode) {
        Objects.requireNonNull(matchMode, "matchMode");
        return switch (matchMode) {
            case STRICT -> strict(stack);
            case ID_ONLY -> idOnly(stack);
        };
    }

    public static StrictCompareKey strict(ItemStack stack) {
        return new StrictCompareKey(stack);
    }

    public static IdOnlyCompareKey idOnly(ItemStack stack) {
        return new IdOnlyCompareKey(stack);
    }

    /**
     * Evaluates one expected compare key against an observed stack.
     */
    public static boolean expectedMatches(CompareKey expected, ItemStack observed) {
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(observed, "observed");
        return expected.matches(observed);
    }

    /**
     * Evaluates one expected compare key against another candidate key.
     * <p>
     * This is directional. A strict expected key will not accept an id-only
     * candidate because the candidate no longer carries enough detail.
     */
    public static boolean expectedMatches(CompareKey expected, CompareKey candidate) {
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(candidate, "candidate");
        return expected.matches(candidate);
    }

    public static boolean sameItemId(ItemStack left, ItemStack right) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        return !left.isEmpty() && !right.isEmpty() && left.is(right.getItem());
    }
}
