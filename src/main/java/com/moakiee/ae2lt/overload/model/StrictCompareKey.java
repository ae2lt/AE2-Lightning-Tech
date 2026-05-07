package com.moakiee.ae2lt.overload.model;

import java.util.Objects;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Compare key that requires the same item id and the same attached
 * data/components.
 */
public final class StrictCompareKey implements CompareKey {
    private final ItemStack template;

    public StrictCompareKey(ItemStack template) {
        Objects.requireNonNull(template, "template");
        if (template.isEmpty()) {
            throw new IllegalArgumentException("template stack must not be empty");
        }

        this.template = template.copy();
        this.template.setCount(1);
    }

    @Override
    public MatchMode mode() {
        return MatchMode.STRICT;
    }

    @Override
    public Item item() {
        return template.getItem();
    }

    /**
     * Returns a defensive copy so callers cannot mutate the internal template.
     */
    public ItemStack template() {
        return template.copy();
    }

    @Override
    public boolean matches(ItemStack stack) {
        return !stack.isEmpty() && ItemStack.isSameItemSameComponents(template, stack);
    }

    @Override
    public boolean matches(CompareKey candidate) {
        Objects.requireNonNull(candidate, "candidate");
        if (candidate instanceof StrictCompareKey strictCandidate) {
            return ItemStack.isSameItemSameComponents(template, strictCandidate.template);
        }
        return false;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof StrictCompareKey other)) {
            return false;
        }
        return ItemStack.isSameItemSameComponents(template, other.template);
    }

    @Override
    public int hashCode() {
        return Objects.hash(template.getItem(), template.getComponents());
    }

    @Override
    public String toString() {
        return "StrictCompareKey[" + template + "]";
    }
}
