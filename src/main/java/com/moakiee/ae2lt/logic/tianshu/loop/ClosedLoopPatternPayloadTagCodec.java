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
    private static final String TAG_SEEDS = "Seeds";
    private static final String TAG_INPUTS = "Inputs";
    private static final String TAG_OUTPUTS = "Outputs";
    private static final String TAG_SEED_MULTIPLIER = "SeedMultiplier";
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
            members.add(memberTag);
        }
        tag.put(TAG_MEMBERS, members);
        tag.put(TAG_SEEDS, writeStacks(payload.seeds(), registries));
        tag.put(TAG_INPUTS, writeStacks(payload.externalInputs(), registries));
        tag.put(TAG_OUTPUTS, writeStacks(payload.netOutputs(), registries));
        tag.putInt(TAG_SEED_MULTIPLIER, payload.seedMultiplier());
        tag.putBoolean(TAG_ENABLED, payload.enabled());
        return tag;
    }

    public static ClosedLoopPatternPayload read(CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.hasUUID(TAG_ID)) throw new IllegalArgumentException("closed-loop payload is missing id");
        var members = new ArrayList<ClosedLoopMemberPattern>();
        var memberTags = tag.getList(TAG_MEMBERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < memberTags.size(); i++) {
            var memberTag = memberTags.getCompound(i);
            var patternTag = memberTag.contains(TAG_MEMBER_PATTERN, Tag.TAG_COMPOUND)
                    ? memberTag.getCompound(TAG_MEMBER_PATTERN) : memberTag;
            members.add(new ClosedLoopMemberPattern(
                    com.moakiee.thunderbolt.ae2.overload.pattern.SourcePatternSnapshot.fromTag(patternTag),
                    Math.max(1L, memberTag.getLong(TAG_MEMBER_COPIES))));
        }
        return new ClosedLoopPatternPayload(
                tag.getUUID(TAG_ID),
                Math.max(1L, tag.getLong(TAG_VERSION)),
                members,
                readStacks(tag.getList(TAG_SEEDS, Tag.TAG_COMPOUND), registries),
                readStacks(tag.getList(TAG_INPUTS, Tag.TAG_COMPOUND), registries),
                readStacks(tag.getList(TAG_OUTPUTS, Tag.TAG_COMPOUND), registries),
                Math.max(1, tag.getInt(TAG_SEED_MULTIPLIER)),
                !tag.contains(TAG_ENABLED, Tag.TAG_BYTE) || tag.getBoolean(TAG_ENABLED));
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
