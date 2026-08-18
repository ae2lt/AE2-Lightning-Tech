package com.moakiee.ae2lt.item;

import java.util.Optional;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.crafting.pattern.EncodedPatternItem;

import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternDecoder;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternPayload;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternPayloadTagCodec;
import com.moakiee.ae2lt.util.ItemStackTagSupport;

/**
 * Tianshu closed-loop pattern item.
 *
 * <p>1.20.1 port notes: AE2's EncodedPatternItem is not generic here, and the
 * item payload lives in a plain ItemStack CompoundTag instead of the 1.21
 * DataComponents.CUSTOM_DATA component. All tag access goes through
 * {@link ItemStackTagSupport} so empty tags stay null on the stack.
 */
public final class ClosedLoopPatternItem extends EncodedPatternItem {
    private static final String TAG_PAYLOAD = "ClosedLoopPattern";
    private static final String TAG_EXECUTION_MEMBER = "ExecutionMember";

    public ClosedLoopPatternItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public boolean hasPayload(ItemStack stack) {
        return ItemStackTagSupport.getTagCopy(stack).contains(TAG_PAYLOAD, CompoundTag.TAG_COMPOUND);
    }

    public Optional<ClosedLoopPatternPayload> readPayload(ItemStack stack, Level level) {
        if (stack == null || level == null || stack.getItem() != this) return Optional.empty();
        var root = ItemStackTagSupport.getTagCopy(stack);
        if (!root.contains(TAG_PAYLOAD, CompoundTag.TAG_COMPOUND)) return Optional.empty();
        try {
            return Optional.of(ClosedLoopPatternPayloadTagCodec.read(
                    root.getCompound(TAG_PAYLOAD)));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    public int readExecutionMember(ItemStack stack) {
        if (stack == null || stack.getItem() != this) return -1;
        var root = ItemStackTagSupport.getTagCopy(stack);
        return root.contains(TAG_EXECUTION_MEMBER, CompoundTag.TAG_INT)
                ? root.getInt(TAG_EXECUTION_MEMBER) : -1;
    }

    public void writePayload(ItemStack stack, ClosedLoopPatternPayload payload,
                             net.minecraft.core.HolderLookup.Provider registries) {
        if (stack == null || stack.getItem() != this) {
            throw new IllegalArgumentException("payload target must be a closed-loop pattern item");
        }
        ItemStackTagSupport.updateTag(stack, root -> root.put(
                TAG_PAYLOAD, ClosedLoopPatternPayloadTagCodec.write(payload)));
    }

    public ItemStack createStack(ClosedLoopPatternPayload payload,
                                 net.minecraft.core.HolderLookup.Provider registries) {
        var stack = new ItemStack(this);
        writePayload(stack, payload, registries);
        return stack;
    }

    public ItemStack createExecutionMemberStack(
            ClosedLoopPatternPayload payload,
            int memberIndex,
            net.minecraft.core.HolderLookup.Provider registries) {
        if (memberIndex < 0 || memberIndex >= payload.memberPatterns().size()) {
            throw new IllegalArgumentException("closed-loop execution member index is out of bounds");
        }
        var stack = createStack(payload, registries);
        ItemStackTagSupport.updateTag(stack,
                root -> root.putInt(TAG_EXECUTION_MEMBER, memberIndex));
        return stack;
    }

    @Override
    public IPatternDetails decode(ItemStack stack, Level level, boolean tryRecovery) {
        return stack.getItem() == this ? ClosedLoopPatternDecoder.INSTANCE.decodePattern(AEItemKey.of(stack), level) : null;
    }

    @Override
    public IPatternDetails decode(AEItemKey what, Level level) {
        return what != null && what.getItem() == this
                ? ClosedLoopPatternDecoder.INSTANCE.decodePattern(what, level) : null;
    }
}
