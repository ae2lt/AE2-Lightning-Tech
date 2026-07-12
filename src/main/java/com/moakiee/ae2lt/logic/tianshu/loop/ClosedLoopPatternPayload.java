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
        int parallelism,
        boolean enabled) {

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
        if (parallelism < 1) throw new IllegalArgumentException("parallelism must be positive");

        var outputKeys = new HashSet<>();
        for (var output : netOutputs) {
            if (!outputKeys.add(output.what())) {
                throw new IllegalArgumentException("duplicate net output key: " + output.what());
            }
        }
    }

    public ClosedLoopPatternPayload withParallelism(int newParallelism) {
        return new ClosedLoopPatternPayload(patternId, version + 1, memberPatterns, seeds,
                externalInputs, netOutputs, newParallelism, enabled);
    }

    public ClosedLoopPatternPayload withEnabled(boolean newEnabled) {
        if (enabled == newEnabled) return this;
        return new ClosedLoopPatternPayload(patternId, version + 1, memberPatterns, seeds,
                externalInputs, netOutputs, parallelism, newEnabled);
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
