package com.moakiee.ae2lt.logic.tianshu.loop;

import appeng.api.stacks.GenericStack;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public final class ClosedLoopPatternPayloadTagCodec {
    private static final String TAG_ID = "Id";
    private static final String TAG_VERSION = "Version";
    private static final String TAG_MEMBERS = "Members";
    private static final String TAG_MEMBER_PATTERN = "Pattern";
    private static final String TAG_MEMBER_COPIES = "Copies";
    private static final String TAG_MEMBER_SEED_WAVE_COPIES = "SeedWaveCopies";
    private static final String TAG_SEEDS = "Seeds";
    private static final String TAG_INPUTS = "Inputs";
    private static final String TAG_OUTPUTS = "Outputs";
    /** Legacy multiplier alias; seed-wave payloads only guarantee old-save to new-code upgrades. */
    private static final String TAG_SEED_MULTIPLIER = "SeedMultiplier";
    private static final String TAG_EXECUTION_SEED_MULTIPLIER = "ExecutionSeedMultiplier";
    private static final String TAG_STORED_TASK_MULTIPLIER = "StoredTaskMultiplier";
    private static final String TAG_ENABLED = "Enabled";

    public static CompoundTag write(ClosedLoopPatternPayload payload, HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.putUUID(TAG_ID, payload.patternId());
        tag.putLong(TAG_VERSION, payload.version());
        var members = new ListTag();
        for (var member : payload.memberPatterns()) {
            var memberTag = new CompoundTag();
            memberTag.put(TAG_MEMBER_PATTERN, member.pattern().toTag());
            memberTag.putLong(TAG_MEMBER_COPIES, member.copiesPerCycle());
            memberTag.putLong(TAG_MEMBER_SEED_WAVE_COPIES, member.seedWaveCopies());
            members.add(memberTag);
        }
        tag.put(TAG_MEMBERS, members);
        tag.put(TAG_SEEDS, writeStacks(payload.seeds(), registries));
        tag.put(TAG_INPUTS, writeStacks(payload.externalInputs(), registries));
        tag.put(TAG_OUTPUTS, writeStacks(payload.netOutputs(), registries));
        tag.putInt(TAG_EXECUTION_SEED_MULTIPLIER, payload.executionSeedMultiplier());
        tag.putInt(TAG_STORED_TASK_MULTIPLIER, payload.storedTaskMultiplier());
        tag.putInt(TAG_SEED_MULTIPLIER, payload.executionSeedMultiplier());
        tag.putBoolean(TAG_ENABLED, payload.enabled());
        return tag;
    }

    public static ClosedLoopPatternPayload read(CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.hasUUID(TAG_ID)) throw new IllegalArgumentException("closed-loop payload is missing id");
        var members = new ArrayList<ClosedLoopMemberPattern>();
        var memberTags = tag.getList(TAG_MEMBERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < memberTags.size(); i++) {
            members.add(readMember(memberTags.getCompound(i)));
        }
        var seedMultipliers = readSeedMultipliers(tag);
        return new ClosedLoopPatternPayload(
                tag.getUUID(TAG_ID),
                Math.max(1L, tag.getLong(TAG_VERSION)),
                members,
                readStacks(tag.getList(TAG_SEEDS, Tag.TAG_COMPOUND), registries),
                readStacks(tag.getList(TAG_INPUTS, Tag.TAG_COMPOUND), registries),
                readStacks(tag.getList(TAG_OUTPUTS, Tag.TAG_COMPOUND), registries),
                seedMultipliers.executionSeedMultiplier(),
                seedMultipliers.storedTaskMultiplier(),
                !tag.contains(TAG_ENABLED, Tag.TAG_BYTE) || tag.getBoolean(TAG_ENABLED));
    }

    static ClosedLoopMemberPattern readMember(CompoundTag memberTag) {
        var patternTag = memberTag.contains(TAG_MEMBER_PATTERN, Tag.TAG_COMPOUND)
                ? memberTag.getCompound(TAG_MEMBER_PATTERN) : memberTag;
        long copies = Math.max(1L, memberTag.getLong(TAG_MEMBER_COPIES));
        long seedWaveCopies = memberTag.contains(
                TAG_MEMBER_SEED_WAVE_COPIES, Tag.TAG_ANY_NUMERIC)
                ? memberTag.getLong(TAG_MEMBER_SEED_WAVE_COPIES)
                : copies;
        return new ClosedLoopMemberPattern(
                com.moakiee.thunderbolt.ae2.overload.pattern.SourcePatternSnapshot.fromTag(patternTag),
                copies,
                seedWaveCopies);
    }

    static SeedMultipliers readSeedMultipliers(CompoundTag tag) {
        int legacySeedMultiplier = positiveIntOrOne(tag, TAG_SEED_MULTIPLIER);
        int executionSeedMultiplier = tag.contains(TAG_EXECUTION_SEED_MULTIPLIER, Tag.TAG_ANY_NUMERIC)
                ? positiveIntOrOne(tag, TAG_EXECUTION_SEED_MULTIPLIER)
                : legacySeedMultiplier;
        return new SeedMultipliers(
                executionSeedMultiplier,
                positiveIntOrOne(tag, TAG_STORED_TASK_MULTIPLIER));
    }

    record SeedMultipliers(int executionSeedMultiplier, int storedTaskMultiplier) {
    }

    private static int positiveIntOrOne(CompoundTag tag, String key) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? Math.max(1, tag.getInt(key)) : 1;
    }

    private static ListTag writeStacks(Iterable<GenericStack> stacks, HolderLookup.Provider registries) {
        var list = new ListTag();
        for (var stack : stacks) list.add(GenericStack.writeTag(registries, stack));
        return list;
    }

    private static java.util.List<GenericStack> readStacks(ListTag tags, HolderLookup.Provider registries) {
        var stacks = new ArrayList<GenericStack>(tags.size());
        for (int i = 0; i < tags.size(); i++) {
            var stack = GenericStack.readTag(registries, tags.getCompound(i));
            if (stack == null) throw new IllegalArgumentException("invalid generic stack in closed-loop payload");
            stacks.add(stack);
        }
        return stacks;
    }

    private ClosedLoopPatternPayloadTagCodec() {
    }
}
