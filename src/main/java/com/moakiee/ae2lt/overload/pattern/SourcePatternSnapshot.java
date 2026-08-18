package com.moakiee.ae2lt.overload.pattern;

import java.util.Objects;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Snapshot of the original plain pattern item that was converted into an
 * overload pattern.
 * <p>
 * The stored stack must remain fully decodable by AE2 later, so we persist the
 * complete serialized item stack instead of only custom data.
 */
public final class SourcePatternSnapshot {
    private static final String TAG_ITEM = "Item";
    private static final String TAG_STACK = "Stack";

    private final ResourceLocation itemId;
    private final CompoundTag serializedStackTag;

    public SourcePatternSnapshot(ResourceLocation itemId, CompoundTag serializedStackTag) {
        this.itemId = Objects.requireNonNull(itemId, "itemId");
        this.serializedStackTag = Objects.requireNonNull(serializedStackTag, "serializedStackTag").copy();
    }

    public static SourcePatternSnapshot fromItemStack(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("source pattern stack must not be empty");
        }

        var itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        var stackTag = stack.save(new CompoundTag());
        return new SourcePatternSnapshot(itemId, stackTag);
    }

    public ResourceLocation itemId() {
        return itemId;
    }

    /**
     * Recreates an equivalent plain-pattern stack for future reparsing.
     */
    public ItemStack toItemStack() {
        return ItemStack.of(serializedStackTag.copy());
    }

    public CompoundTag toTag() {
        var tag = new CompoundTag();
        tag.putString(TAG_ITEM, itemId.toString());
        tag.put(TAG_STACK, serializedStackTag.copy());
        return tag;
    }

    public static SourcePatternSnapshot fromTag(CompoundTag tag) {
        ResourceLocation itemId;
        if (tag.contains(TAG_ITEM, Tag.TAG_STRING)) {
            itemId = new ResourceLocation(tag.getString(TAG_ITEM));
        } else if (tag.contains(TAG_STACK, Tag.TAG_COMPOUND)) {
            itemId = new ResourceLocation(tag.getCompound(TAG_STACK).getString("id"));
        } else {
            throw new IllegalArgumentException("source pattern snapshot is missing an item id");
        }

        if (!tag.contains(TAG_STACK, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("source pattern snapshot is missing a serialized stack");
        }
        return new SourcePatternSnapshot(itemId, tag.getCompound(TAG_STACK));
    }
}
