package com.moakiee.ae2lt.logic.tianshu.terminal;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Persistent authoring state owned by a Tianshu pattern terminal.
 *
 * <p>The menu remains authoritative while it is open, but keeping a snapshot on the host lets a
 * player close and reopen the terminal without losing an unencoded closed-loop draft.
 */
public record ClosedLoopTerminalDraft(
        ItemStack source,
        List<ItemStack> members,
        List<Long> memberCopies,
        List<ItemStack> outputs,
        List<Integer> outputRoles,
        int executionSeedMultiplier,
        int storedTaskMultiplier,
        @Nullable UUID originalPatternId,
        long originalPatternVersion,
        boolean representsEncodedPattern) {
    private static final String TAG_SOURCE = "Source";
    private static final String TAG_MEMBERS = "Members";
    private static final String TAG_MEMBER_COPIES = "MemberCopies";
    private static final String TAG_OUTPUTS = "Outputs";
    private static final String TAG_OUTPUT_ROLES = "OutputRoles";
    private static final String TAG_EXECUTION_MULTIPLIER = "ExecutionSeedMultiplier";
    private static final String TAG_STORED_MULTIPLIER = "StoredTaskMultiplier";
    private static final String TAG_ORIGINAL_ID = "OriginalPatternId";
    private static final String TAG_ORIGINAL_VERSION = "OriginalPatternVersion";
    private static final String TAG_REPRESENTS_ENCODED = "RepresentsEncodedPattern";
    private static final String TAG_SLOT = "Slot";

    public ClosedLoopTerminalDraft {
        source = source == null ? ItemStack.EMPTY : source.copy();
        members = copyStacks(members, ClosedLoopDraftSync.MEMBER_SLOTS, "members");
        outputs = copyStacks(outputs, ClosedLoopDraftSync.OUTPUT_SLOTS, "outputs");
        memberCopies = List.copyOf(memberCopies);
        outputRoles = List.copyOf(outputRoles);
        if (memberCopies.size() != ClosedLoopDraftSync.MEMBER_SLOTS) {
            throw new IllegalArgumentException("invalid closed-loop member-copy count");
        }
        if (outputRoles.size() != ClosedLoopDraftSync.OUTPUT_SLOTS) {
            throw new IllegalArgumentException("invalid closed-loop output-role count");
        }
        for (long copies : memberCopies) {
            if (copies < 0L || copies == Long.MAX_VALUE) {
                throw new IllegalArgumentException("invalid closed-loop member copies");
            }
        }
        for (int role : outputRoles) {
            if (role < 0 || role > 2) {
                throw new IllegalArgumentException("invalid closed-loop output role");
            }
        }
        if (executionSeedMultiplier < 1 || storedTaskMultiplier < 1) {
            throw new IllegalArgumentException("invalid closed-loop multiplier");
        }
        originalPatternVersion = Math.max(0L, originalPatternVersion);
    }

    public CompoundTag write(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.put(TAG_SOURCE, source.saveOptional(registries));
        tag.put(TAG_MEMBERS, writeStacks(members, registries));
        tag.putLongArray(TAG_MEMBER_COPIES, memberCopies.stream().mapToLong(Long::longValue).toArray());
        tag.put(TAG_OUTPUTS, writeStacks(outputs, registries));
        tag.putIntArray(TAG_OUTPUT_ROLES, outputRoles.stream().mapToInt(Integer::intValue).toArray());
        tag.putInt(TAG_EXECUTION_MULTIPLIER, executionSeedMultiplier);
        tag.putInt(TAG_STORED_MULTIPLIER, storedTaskMultiplier);
        if (originalPatternId != null) tag.putUUID(TAG_ORIGINAL_ID, originalPatternId);
        tag.putLong(TAG_ORIGINAL_VERSION, originalPatternVersion);
        tag.putBoolean(TAG_REPRESENTS_ENCODED, representsEncodedPattern);
        return tag;
    }

    @Nullable
    public static ClosedLoopTerminalDraft read(
            CompoundTag tag, HolderLookup.Provider registries) {
        try {
            var source = ItemStack.parseOptional(registries, tag.getCompound(TAG_SOURCE));
            if (source.isEmpty()) return null;
            var copiesArray = tag.getLongArray(TAG_MEMBER_COPIES);
            var rolesArray = tag.getIntArray(TAG_OUTPUT_ROLES);
            if (copiesArray.length != ClosedLoopDraftSync.MEMBER_SLOTS
                    || rolesArray.length != ClosedLoopDraftSync.OUTPUT_SLOTS) {
                return null;
            }
            var copies = new ArrayList<Long>(copiesArray.length);
            for (long value : copiesArray) copies.add(value);
            var roles = new ArrayList<Integer>(rolesArray.length);
            for (int value : rolesArray) roles.add(value);
            return new ClosedLoopTerminalDraft(
                    source,
                    readStacks(tag.getList(TAG_MEMBERS, Tag.TAG_COMPOUND),
                            ClosedLoopDraftSync.MEMBER_SLOTS, registries),
                    copies,
                    readStacks(tag.getList(TAG_OUTPUTS, Tag.TAG_COMPOUND),
                            ClosedLoopDraftSync.OUTPUT_SLOTS, registries),
                    roles,
                    Math.max(1, tag.getInt(TAG_EXECUTION_MULTIPLIER)),
                    Math.max(1, tag.getInt(TAG_STORED_MULTIPLIER)),
                    tag.hasUUID(TAG_ORIGINAL_ID) ? tag.getUUID(TAG_ORIGINAL_ID) : null,
                    tag.getLong(TAG_ORIGINAL_VERSION),
                    tag.getBoolean(TAG_REPRESENTS_ENCODED));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static boolean sameState(
            @Nullable ClosedLoopTerminalDraft left,
            @Nullable ClosedLoopTerminalDraft right) {
        if (left == right) return true;
        if (left == null || right == null
                || !ItemStack.matches(left.source, right.source)
                || !sameStacks(left.members, right.members)
                || !sameStacks(left.outputs, right.outputs)) {
            return false;
        }
        return left.memberCopies.equals(right.memberCopies)
                && left.outputRoles.equals(right.outputRoles)
                && left.executionSeedMultiplier == right.executionSeedMultiplier
                && left.storedTaskMultiplier == right.storedTaskMultiplier
                && Objects.equals(left.originalPatternId, right.originalPatternId)
                && left.originalPatternVersion == right.originalPatternVersion
                && left.representsEncodedPattern == right.representsEncodedPattern;
    }

    private static List<ItemStack> copyStacks(
            List<ItemStack> stacks, int expectedSize, String name) {
        if (stacks == null || stacks.size() != expectedSize) {
            throw new IllegalArgumentException("invalid closed-loop " + name + " count");
        }
        var result = new ArrayList<ItemStack>(expectedSize);
        for (var stack : stacks) result.add(stack == null ? ItemStack.EMPTY : stack.copy());
        return List.copyOf(result);
    }

    private static ListTag writeStacks(
            List<ItemStack> stacks, HolderLookup.Provider registries) {
        var result = new ListTag();
        for (int slot = 0; slot < stacks.size(); slot++) {
            var stack = stacks.get(slot);
            if (stack.isEmpty()) continue;
            var entry = new CompoundTag();
            entry.putInt(TAG_SLOT, slot);
            result.add(stack.save(registries, entry));
        }
        return result;
    }

    private static List<ItemStack> readStacks(
            ListTag entries, int size, HolderLookup.Provider registries) {
        var result = new ArrayList<ItemStack>(size);
        for (int i = 0; i < size; i++) result.add(ItemStack.EMPTY);
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.getCompound(i);
            int slot = entry.getInt(TAG_SLOT);
            if (slot >= 0 && slot < size) {
                result.set(slot, ItemStack.parseOptional(registries, entry));
            }
        }
        return List.copyOf(result);
    }

    private static boolean sameStacks(List<ItemStack> left, List<ItemStack> right) {
        if (left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) {
            if (!ItemStack.matches(left.get(i), right.get(i))) return false;
        }
        return true;
    }
}
