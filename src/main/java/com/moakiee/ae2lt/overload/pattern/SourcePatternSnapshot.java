package com.moakiee.ae2lt.overload.pattern;

import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

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
    private static final String TAG_CUSTOM_DATA = "CustomData";

    private final ResourceLocation itemId;
    @Nullable
    private final CompoundTag serializedStackTag;
    @Nullable
    private final CompoundTag customDataTag;

    public SourcePatternSnapshot(ResourceLocation itemId,
                                 @Nullable CompoundTag serializedStackTag,
                                 @Nullable CompoundTag customDataTag) {
        this.itemId = Objects.requireNonNull(itemId, "itemId");
        this.serializedStackTag = serializedStackTag == null ? null : serializedStackTag.copy();
        this.customDataTag = customDataTag == null ? null : customDataTag.copy();
    }

    public static SourcePatternSnapshot fromItemStack(ItemStack stack, HolderLookup.Provider registries) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(registries, "registries");
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("source pattern stack must not be empty");
        }

        var itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        var serializedStack = stack.saveOptional(registries);
        if (!(serializedStack instanceof CompoundTag stackTag)) {
            throw new IllegalStateException("serialized source pattern stack was not a compound tag");
        }
        return new SourcePatternSnapshot(itemId, stackTag, null);
    }

    public ResourceLocation itemId() {
        return itemId;
    }

    @Nullable
    public CompoundTag customDataTag() {
        return customDataTag == null ? null : customDataTag.copy();
    }

    /**
     * Recreates an equivalent plain-pattern stack for future reparsing.
     */
    public ItemStack toItemStack(HolderLookup.Provider registries) {
        Objects.requireNonNull(registries, "registries");

        if (serializedStackTag != null && !serializedStackTag.isEmpty()) {
            return ItemStack.parseOptional(registries, serializedStackTag.copy());
        }

        // Backward compatibility for older overload patterns that only stored
        // item id + custom data.
        var item = BuiltInRegistries.ITEM.get(itemId);
        var stack = new ItemStack(item);
        if (customDataTag != null && !customDataTag.isEmpty()) {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(customDataTag.copy()));
        }
        return stack;
    }

    public CompoundTag toTag() {
        var tag = new CompoundTag();
        tag.putString(TAG_ITEM, itemId.toString());
        if (serializedStackTag != null && !serializedStackTag.isEmpty()) {
            tag.put(TAG_STACK, serializedStackTag.copy());
        } else if (customDataTag != null && !customDataTag.isEmpty()) {
            tag.put(TAG_CUSTOM_DATA, customDataTag.copy());
        }
        return tag;
    }

    public static SourcePatternSnapshot fromTag(CompoundTag tag) {
        ResourceLocation itemId;
        if (tag.contains(TAG_ITEM, Tag.TAG_STRING)) {
            itemId = ResourceLocation.parse(tag.getString(TAG_ITEM));
        } else if (tag.contains(TAG_STACK, Tag.TAG_COMPOUND)) {
            itemId = ResourceLocation.parse(tag.getCompound(TAG_STACK).getString("id"));
        } else {
            throw new IllegalArgumentException("source pattern snapshot is missing an item id");
        }

        CompoundTag serializedStack = null;
        if (tag.contains(TAG_STACK, Tag.TAG_COMPOUND)) {
            serializedStack = tag.getCompound(TAG_STACK).copy();
        }

        CompoundTag customData = null;
        if (tag.contains(TAG_CUSTOM_DATA, CompoundTag.TAG_COMPOUND)) {
            customData = tag.getCompound(TAG_CUSTOM_DATA).copy();
        }
        return new SourcePatternSnapshot(itemId, serializedStack, customData);
    }
}
