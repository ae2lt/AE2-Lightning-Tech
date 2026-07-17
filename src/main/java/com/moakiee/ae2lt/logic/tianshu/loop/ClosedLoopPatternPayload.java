package com.moakiee.ae2lt.logic.tianshu.loop;

import appeng.api.stacks.GenericStack;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable encoded definition of one contracted closed-loop production node. */
public record ClosedLoopPatternPayload(
        UUID patternId,
        long version,
        List<ClosedLoopMemberPattern> memberPatterns,
        List<GenericStack> seeds,
        List<GenericStack> externalInputs,
        List<GenericStack> netOutputs,
        int executionSeedMultiplier,
        int storedTaskMultiplier,
        boolean enabled) {

    /** Binary/source compatibility for integrations compiled against the single multiplier API. */
    @Deprecated
    public ClosedLoopPatternPayload(
            UUID patternId,
            long version,
            List<ClosedLoopMemberPattern> memberPatterns,
            List<GenericStack> seeds,
            List<GenericStack> externalInputs,
            List<GenericStack> netOutputs,
            int seedMultiplier,
            boolean enabled) {
        this(patternId, version, memberPatterns, seeds, externalInputs, netOutputs,
                seedMultiplier, 1, enabled);
    }

    public ClosedLoopPatternPayload {
        patternId = Objects.requireNonNull(patternId, "patternId");
        if (version < 1) throw new IllegalArgumentException("closed-loop pattern version must be positive");
        memberPatterns = copyMembers(memberPatterns);
        seeds = copyStacks(seeds, "seeds");
        externalInputs = copyStacks(externalInputs, "externalInputs");
        netOutputs = copyStacks(netOutputs, "netOutputs");
        if (memberPatterns.isEmpty()) throw new IllegalArgumentException("closed-loop pattern requires members");
        if (seeds.isEmpty()) throw new IllegalArgumentException("closed-loop pattern requires at least one seed");
        if (netOutputs.isEmpty()) throw new IllegalArgumentException("closed-loop pattern requires a net output");
        if (executionSeedMultiplier < 1) {
            throw new IllegalArgumentException("execution seed multiplier must be positive");
        }
        if (storedTaskMultiplier < 1) {
            throw new IllegalArgumentException("stored task multiplier must be positive");
        }
        long seedWaveRepetitions = memberPatterns.getFirst().seedWaveRepetitions();
        for (var member : memberPatterns) {
            if (member.seedWaveRepetitions() != seedWaveRepetitions) {
                throw new IllegalArgumentException(
                        "all closed-loop members must use the same seed-wave repetition count");
            }
        }

        var outputKeys = new HashSet<>();
        for (var output : netOutputs) {
            if (!outputKeys.add(output.what())) {
                throw new IllegalArgumentException("duplicate net output key: " + output.what());
            }
        }
    }

    public ClosedLoopPatternPayload withExecutionSeedMultiplier(int newExecutionSeedMultiplier) {
        return withSeedMultipliers(newExecutionSeedMultiplier, storedTaskMultiplier);
    }

    /** Legacy name; a single multiplier now means per-job borrowed seed only. */
    @Deprecated
    public int seedMultiplier() {
        return executionSeedMultiplier;
    }

    /** Legacy name; preserves the independently configured stored-task capacity. */
    @Deprecated
    public ClosedLoopPatternPayload withSeedMultiplier(int newSeedMultiplier) {
        return withExecutionSeedMultiplier(newSeedMultiplier);
    }

    public ClosedLoopPatternPayload withStoredTaskMultiplier(int newStoredTaskMultiplier) {
        return withSeedMultipliers(executionSeedMultiplier, newStoredTaskMultiplier);
    }

    public ClosedLoopPatternPayload withSeedMultipliers(
            int newExecutionSeedMultiplier, int newStoredTaskMultiplier) {
        if (executionSeedMultiplier == newExecutionSeedMultiplier
                && storedTaskMultiplier == newStoredTaskMultiplier) {
            return this;
        }
        return new ClosedLoopPatternPayload(patternId, version + 1, memberPatterns, seeds,
                externalInputs, netOutputs, newExecutionSeedMultiplier, newStoredTaskMultiplier, enabled);
    }

    public ClosedLoopPatternPayload withEnabled(boolean newEnabled) {
        if (enabled == newEnabled) return this;
        return new ClosedLoopPatternPayload(patternId, version + 1, memberPatterns, seeds,
                externalInputs, netOutputs, executionSeedMultiplier, storedTaskMultiplier, newEnabled);
    }

    /** Derived execution scale; it is intentionally not a third persisted/UI multiplier. */
    public long seedWaveRepetitions() {
        return memberPatterns.getFirst().seedWaveRepetitions();
    }

    private static List<ClosedLoopMemberPattern> copyMembers(List<ClosedLoopMemberPattern> members) {
        Objects.requireNonNull(members, "memberPatterns");
        for (var member : members) Objects.requireNonNull(member, "memberPattern");
        return List.copyOf(members);
    }

    private static List<GenericStack> copyStacks(List<GenericStack> stacks, String name) {
        Objects.requireNonNull(stacks, name);
        for (var stack : stacks) {
            Objects.requireNonNull(stack, name + " entry");
            Objects.requireNonNull(stack.what(), name + " key");
            if (stack.amount() <= 0) throw new IllegalArgumentException(name + " amounts must be positive");
        }
        return List.copyOf(stacks);
    }
}
