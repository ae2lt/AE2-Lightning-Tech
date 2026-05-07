package com.moakiee.ae2lt.item;

import java.util.Objects;
import java.util.Optional;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.crafting.pattern.EncodedPatternItem;

import com.moakiee.ae2lt.overload.model.EncodedOverloadPattern;
import com.moakiee.ae2lt.overload.pattern.OverloadPatternDecoder;
import com.moakiee.ae2lt.overload.pattern.OverloadPatternPayload;
import com.moakiee.ae2lt.overload.pattern.OverloadPatternPayloadTagCodec;
import com.moakiee.ae2lt.overload.pattern.PatternExecutionHostKind;
import com.moakiee.ae2lt.overload.pattern.SourcePatternSnapshot;

/**
 * Final item form of an overload pattern.
 * <p>
 * This item has a dedicated identity and carries its own host-bound overload
 * payload. It must not be treated as a transparent variant of a normal AE2
 * pattern item.
 */
public class OverloadPatternItem extends EncodedPatternItem {
    private static final String TAG_OVERLOAD_PATTERN = "OverloadPattern";

    public OverloadPatternItem(net.minecraft.world.item.Item.Properties properties) {
        super(properties.stacksTo(1));
    }

    public boolean hasPayload(ItemStack stack) {
        return readRootTag(stack).contains(TAG_OVERLOAD_PATTERN, CompoundTag.TAG_COMPOUND);
    }

    public Optional<OverloadPatternPayload> readPayload(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        var rootTag = readRootTag(stack);
        if (!rootTag.contains(TAG_OVERLOAD_PATTERN, CompoundTag.TAG_COMPOUND)) {
            return Optional.empty();
        }

        var payloadTag = rootTag.getCompound(TAG_OVERLOAD_PATTERN);
        return Optional.of(OverloadPatternPayloadTagCodec.readPayload(payloadTag));
    }

    public Optional<EncodedOverloadPattern> readEncodedPattern(ItemStack stack) {
        return readPayload(stack).map(OverloadPatternPayload::encodedPattern);
    }

    public Optional<SourcePatternSnapshot> readSourcePattern(ItemStack stack) {
        return readPayload(stack).map(OverloadPatternPayload::sourcePattern);
    }

    public PatternExecutionHostKind requiredHostKind(ItemStack stack) {
        return readPayload(stack)
                .map(OverloadPatternPayload::requiredHostKind)
                .orElse(PatternExecutionHostKind.OVERLOADED_PATTERN_PROVIDER);
    }

    public void writePayload(ItemStack stack, OverloadPatternPayload payload) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(payload, "payload");

        com.moakiee.ae2lt.util.ItemStackTagSupport.updateTag(stack, rootTag -> {
            rootTag.put(TAG_OVERLOAD_PATTERN, OverloadPatternPayloadTagCodec.writePayload(payload));
        });
    }

    public void writeEncodedPattern(ItemStack stack, EncodedOverloadPattern encodedPattern) {
        Objects.requireNonNull(encodedPattern, "encodedPattern");

        var payload = readPayload(stack).orElseThrow(() ->
                new IllegalStateException("cannot update encoded overload pattern without existing payload"));
        writePayload(stack, new OverloadPatternPayload(
                payload.requiredHostKind(),
                payload.sourcePattern(),
                encodedPattern));
    }

    public ItemStack createStack(OverloadPatternPayload payload) {
        var stack = new ItemStack(this);
        writePayload(stack, payload);
        return stack;
    }

    public IPatternDetails decode(ItemStack stack, Level level) {
        return stack.getItem() == this ? OverloadPatternDecoder.INSTANCE.decodePattern(stack, level) : null;
    }

    @Override
    public IPatternDetails decode(ItemStack stack, Level level, boolean tryRecovery) {
        return decode(stack, level);
    }

    @Override
    public IPatternDetails decode(AEItemKey what, Level level) {
        return what != null && what.getItem() == this ? decode(what.toStack(), level) : null;
    }

    private static CompoundTag readRootTag(ItemStack stack) {
        return com.moakiee.ae2lt.util.ItemStackTagSupport.getTagCopy(stack);
    }
}
