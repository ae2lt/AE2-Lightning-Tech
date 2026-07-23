package com.moakiee.ae2lt.celestweave.state;

import com.mojang.serialization.Codec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * Wraps an ItemStack for the {@code CELESTWEAVE_STRUCTURAL_CORE} data component.
 * {@code ItemStack.equals} is {@code Object.equals} (reference), so two
 * identical-looking copies from network deserialisation are never equal.
 * This record's {@code equals} uses {@link ItemStack#matches} instead,
 * fixing the same class of bug as {@link CelestweaveModuleContainer} (issue #20).
 * 
 * @author howxu &lt;dev@howxu.cn&gt;
 */
public record StructuralCoreWrapper(ItemStack stack) {

    public static final StructuralCoreWrapper EMPTY = new StructuralCoreWrapper(ItemStack.EMPTY);

    public static final Codec<StructuralCoreWrapper> CODEC =
            ItemStack.OPTIONAL_CODEC.xmap(StructuralCoreWrapper::new, StructuralCoreWrapper::stack);

    public static final StreamCodec<RegistryFriendlyByteBuf, StructuralCoreWrapper> STREAM_CODEC =
            ItemStack.OPTIONAL_STREAM_CODEC.map(StructuralCoreWrapper::new, StructuralCoreWrapper::stack);

    public StructuralCoreWrapper {
        stack = stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof StructuralCoreWrapper other)) {
            return false;
        }
        return ItemStack.matches(stack, other.stack);
    }

    @Override
    public int hashCode() {
        return ItemStack.hashItemAndComponents(stack);
    }
}