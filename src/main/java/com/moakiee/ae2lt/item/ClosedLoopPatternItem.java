package com.moakiee.ae2lt.item;

import appeng.api.crafting.IPatternDetails;
import appeng.crafting.pattern.EncodedPatternItem;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternDecoder;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternPayload;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternPayloadTagCodec;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

public final class ClosedLoopPatternItem extends EncodedPatternItem<IPatternDetails> {
    private static final String TAG_PAYLOAD = "ClosedLoopPattern";
    private static final String TAG_EXECUTION_MEMBER = "ExecutionMember";

    public ClosedLoopPatternItem(Properties properties) {
        super(properties.stacksTo(1), ClosedLoopPatternDecoder.INSTANCE::decodePattern, null);
    }

    public boolean hasPayload(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().contains(TAG_PAYLOAD, net.minecraft.nbt.Tag.TAG_COMPOUND);
    }

    public Optional<ClosedLoopPatternPayload> readPayload(ItemStack stack, Level level) {
        if (stack == null || level == null || stack.getItem() != this) return Optional.empty();
        var root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!root.contains(TAG_PAYLOAD, net.minecraft.nbt.Tag.TAG_COMPOUND)) return Optional.empty();
        try {
            return Optional.of(ClosedLoopPatternPayloadTagCodec.read(
                    root.getCompound(TAG_PAYLOAD), level.registryAccess()));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    public int readExecutionMember(ItemStack stack) {
        if (stack == null || stack.getItem() != this) return -1;
        var root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return root.contains(TAG_EXECUTION_MEMBER, net.minecraft.nbt.Tag.TAG_INT)
                ? root.getInt(TAG_EXECUTION_MEMBER) : -1;
    }

    public void writePayload(ItemStack stack, ClosedLoopPatternPayload payload,
                             net.minecraft.core.HolderLookup.Provider registries) {
        if (stack == null || stack.getItem() != this) {
            throw new IllegalArgumentException("payload target must be a closed-loop pattern item");
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> root.put(
                TAG_PAYLOAD, ClosedLoopPatternPayloadTagCodec.write(payload, registries)));
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
        CustomData.update(DataComponents.CUSTOM_DATA, stack,
                root -> root.putInt(TAG_EXECUTION_MEMBER, memberIndex));
        return stack;
    }

    @Override
    public IPatternDetails decode(ItemStack stack, Level level) {
        return stack.getItem() == this ? ClosedLoopPatternDecoder.INSTANCE.decodePattern(stack, level) : null;
    }
}
