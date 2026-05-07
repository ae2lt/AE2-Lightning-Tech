package com.moakiee.ae2lt.overload.model;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Pattern-local comparison token for overload matching.
 * <p>
 * This is intentionally separate from AE2's {@code AEKey}. It captures how one
 * expected slot wants to compare an observed stack without changing any global
 * storage or crafting semantics.
 * <p>
 * {@link #matches(CompareKey)} is directional: "does this expected key accept
 * that candidate key?" Callers should not assume symmetric behavior.
 */
public sealed interface CompareKey permits IdOnlyCompareKey, StrictCompareKey {
    MatchMode mode();

    Item item();

    boolean matches(ItemStack stack);

    boolean matches(CompareKey candidate);
}
