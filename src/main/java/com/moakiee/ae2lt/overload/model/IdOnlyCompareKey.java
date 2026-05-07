package com.moakiee.ae2lt.overload.model;

import java.util.Objects;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Compare key that only cares about item identity and ignores attached
 * data/components.
 */
public final class IdOnlyCompareKey implements CompareKey {
    private final Item item;

    public IdOnlyCompareKey(Item item) {
        this.item = Objects.requireNonNull(item, "item");
    }

    public IdOnlyCompareKey(ItemStack template) {
        this(requireNonEmpty(template).getItem());
    }

    @Override
    public MatchMode mode() {
        return MatchMode.ID_ONLY;
    }

    @Override
    public Item item() {
        return item;
    }

    @Override
    public boolean matches(ItemStack stack) {
        return !stack.isEmpty() && stack.is(item);
    }

    @Override
    public boolean matches(CompareKey candidate) {
        Objects.requireNonNull(candidate, "candidate");
        return item == candidate.item();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof IdOnlyCompareKey other)) {
            return false;
        }
        return item == other.item;
    }

    @Override
    public int hashCode() {
        return Item.getId(item);
    }

    @Override
    public String toString() {
        return "IdOnlyCompareKey[" + item + "]";
    }

    private static ItemStack requireNonEmpty(ItemStack stack) {
        Objects.requireNonNull(stack, "template");
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("template stack must not be empty");
        }
        return stack;
    }
}
