package com.moakiee.ae2lt.overload.runtime.pattern;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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
    // Lazily computed from immutable state; benign race (String is safely publishable).
    @Nullable
    private String cachedFingerprint;

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

    /** Stable content fingerprint used to keep distinct source recipes in distinct pending slots. */
    public String fingerprint() {
        var cached = cachedFingerprint;
        if (cached != null) {
            return cached;
        }
        var computed = computeFingerprint();
        cachedFingerprint = computed;
        return computed;
    }

    private String computeFingerprint() {
        var identity = toTag();
        if (identity.contains(TAG_STACK, Tag.TAG_COMPOUND)) {
            // The stack count is transport state, not recipe identity. Pattern providers may hand
            // us an otherwise identical encoded pattern as a stack of 1 or 64; keeping that count
            // would split one recipe into unrelated overload pending queues. Only normalize the
            // serialized stack's top-level count so recipe-internal ingredient counts remain part
            // of the fingerprint.
            var stack = identity.getCompound(TAG_STACK);
            stack.remove("count");
            stack.remove("Count"); // legacy ItemStack NBT
        }
        var canonical = canonicalCopy(identity).toString();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
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

    private static Tag canonicalCopy(Tag source) {
        if (source instanceof CompoundTag compound) {
            var result = new CompoundTag();
            compound.getAllKeys().stream().sorted().forEach(key -> {
                var value = compound.get(key);
                if (value != null) result.put(key, canonicalCopy(value));
            });
            return result;
        }
        if (source instanceof ListTag list) {
            var result = new ListTag();
            for (var value : list) result.add(canonicalCopy(value));
            return result;
        }
        return source.copy();
    }
}
