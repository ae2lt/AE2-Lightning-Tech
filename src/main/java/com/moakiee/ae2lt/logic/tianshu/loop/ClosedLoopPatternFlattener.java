package com.moakiee.ae2lt.logic.tianshu.loop;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import com.moakiee.ae2lt.item.ClosedLoopPatternItem;
import com.moakiee.thunderbolt.ae2.overload.pattern.SourcePatternSnapshot;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.world.level.Level;

/**
 * Compile-time expansion of nested closed-loop pattern references.
 *
 * <p>The runtime payload remains flat. A nested pattern contributes only its ordinary leaf
 * patterns and stoichiometric copy counts; its execution and storage multipliers are deliberately
 * ignored because they configure the nested pattern as an independent job, which no longer exists
 * after expansion.</p>
 */
public final class ClosedLoopPatternFlattener {
    public static final int MAX_NESTING_DEPTH = 8;
    static final int MAX_EXPANDED_NODES = 64;

    /** Expands a draft through AE2's real decoder path. */
    public static Result flatten(List<ClosedLoopMemberPattern> draftMembers, Level level) {
        if (level == null) return failure(Status.INVALID_INPUT);
        return flatten(draftMembers, snapshot -> resolve(snapshot, level));
    }

    /** Package-visible resolver seam for deterministic recursion and corruption tests. */
    static Result flatten(List<ClosedLoopMemberPattern> draftMembers, MemberResolver resolver) {
        if (draftMembers == null || draftMembers.isEmpty() || resolver == null) {
            return failure(Status.INVALID_INPUT);
        }

        var state = new ExpansionState(resolver);
        for (var member : draftMembers) {
            if (member == null) return failure(Status.INVALID_INPUT);
            state.expand(member, 1L, 0);
            if (state.failure != null) return failure(state.failure);
        }
        if (state.leaves.isEmpty()) return failure(Status.INVALID_INPUT);

        // Each recursively imported macro establishes a lower bound for one safe seed wave. The
        // greatest common repeat shared by every leaf is the only repeat that can be removed while
        // retaining one ordinary RoutingPlan. A top-level ordinary leaf has repeat 1 and therefore
        // intentionally disables this optimization for the whole flat payload.
        long commonRepeat = 0L;
        for (var leaf : state.leaves) {
            if (leaf.totalCopies % leaf.minimumSeedWaveCopies != 0L) {
                return failure(Status.INCOMPATIBLE_SEED_WAVES);
            }
            long repeat = leaf.totalCopies / leaf.minimumSeedWaveCopies;
            if (repeat < 1L) return failure(Status.INCOMPATIBLE_SEED_WAVES);
            commonRepeat = commonRepeat == 0L ? repeat : gcd(commonRepeat, repeat);
        }
        if (commonRepeat < 1L) return failure(Status.INCOMPATIBLE_SEED_WAVES);

        var flattened = new ArrayList<LeafMember>(state.leaves.size());
        for (var leaf : state.leaves) {
            if (leaf.totalCopies % commonRepeat != 0L) {
                return failure(Status.INCOMPATIBLE_SEED_WAVES);
            }
            long seedWaveCopies = leaf.totalCopies / commonRepeat;
            if (seedWaveCopies < leaf.minimumSeedWaveCopies) {
                return failure(Status.INCOMPATIBLE_SEED_WAVES);
            }
            flattened.add(new LeafMember(
                    leaf.details, leaf.snapshot, leaf.totalCopies, seedWaveCopies));
        }
        return new Result(Status.VALID, flattened, commonRepeat);
    }

    private static ResolvedMember resolve(SourcePatternSnapshot snapshot, Level level) {
        try {
            var stack = snapshot.toItemStack(level.registryAccess());
            if (stack.isEmpty()) return null;

            // Inspect the raw item before decode. Execution-member persistence stacks decode to a
            // ClosedLoopExpandedPatternDetails leaf and would otherwise bypass the macro check.
            if (stack.getItem() instanceof ClosedLoopPatternItem item) {
                if (item.readExecutionMember(stack) >= 0) {
                    return ResolvedMember.executionReference();
                }
                var payload = item.readPayload(stack, level).orElse(null);
                return payload != null ? ResolvedMember.macro(payload) : null;
            }

            var details = PatternDetailsHelper.decodePattern(stack, level);
            if (details == null) return null;
            if (details instanceof TianshuClosedLoopPatternDetails nested) {
                return ResolvedMember.macro(nested.closedLoopPayload());
            }
            return ResolvedMember.leaf(details);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static long gcd(long left, long right) {
        while (right != 0L) {
            long remainder = left % right;
            left = right;
            right = remainder;
        }
        return left;
    }

    private static Result failure(Status status) {
        return new Result(status, List.of(), 0L);
    }

    public enum Status {
        VALID,
        INVALID_INPUT,
        MEMBER_UNDECODABLE,
        EXECUTION_MEMBER_REFERENCE,
        NESTING_TOO_DEEP,
        CYCLIC_REFERENCE,
        TOO_MANY_MEMBERS,
        ARITHMETIC_OVERFLOW,
        INCOMPATIBLE_SEED_WAVES
    }

    /** One fully decoded ordinary member in the canonical flat payload. */
    public record LeafMember(
            IPatternDetails details,
            SourcePatternSnapshot snapshot,
            long totalCopies,
            long seedWaveCopies) {
        public LeafMember {
            Objects.requireNonNull(details, "details");
            Objects.requireNonNull(snapshot, "snapshot");
            if (totalCopies < 1L || seedWaveCopies < 1L || seedWaveCopies > totalCopies
                    || totalCopies % seedWaveCopies != 0L) {
                throw new IllegalArgumentException("invalid closed-loop leaf wave counts");
            }
        }
    }

    public record Result(Status status, List<LeafMember> members, long commonRepeat) {
        public Result {
            status = Objects.requireNonNull(status, "status");
            members = List.copyOf(members);
            if (status == Status.VALID) {
                if (members.isEmpty() || commonRepeat < 1L) {
                    throw new IllegalArgumentException("valid flatten result requires members");
                }
            } else if (!members.isEmpty() || commonRepeat != 0L) {
                throw new IllegalArgumentException("failed flatten result must not expose members");
            }
        }

        public boolean valid() {
            return status == Status.VALID;
        }
    }

    @FunctionalInterface
    interface MemberResolver {
        ResolvedMember resolve(SourcePatternSnapshot snapshot);
    }

    record ResolvedMember(
            IPatternDetails details,
            ClosedLoopPatternPayload nestedPayload,
            boolean executionMember) {
        static ResolvedMember leaf(IPatternDetails details) {
            return new ResolvedMember(Objects.requireNonNull(details, "details"), null, false);
        }

        static ResolvedMember macro(ClosedLoopPatternPayload payload) {
            return new ResolvedMember(null, Objects.requireNonNull(payload, "payload"), false);
        }

        static ResolvedMember executionReference() {
            return new ResolvedMember(null, null, true);
        }

        ResolvedMember {
            if (executionMember) {
                if (details != null || nestedPayload != null) {
                    throw new IllegalArgumentException("execution member resolution is exclusive");
                }
            } else if ((details == null) == (nestedPayload == null)) {
                throw new IllegalArgumentException("resolved member must be exactly one kind");
            }
        }
    }

    private static final class ExpansionState {
        private final MemberResolver resolver;
        private final List<RawLeaf> leaves = new ArrayList<>();
        private final Set<java.util.UUID> activePatternIds = new HashSet<>();
        private int expandedNodes;
        private Status failure;

        private ExpansionState(MemberResolver resolver) {
            this.resolver = resolver;
        }

        private void expand(ClosedLoopMemberPattern member, long parentScale, int depth) {
            if (failure != null) return;
            if (++expandedNodes > MAX_EXPANDED_NODES) {
                failure = Status.TOO_MANY_MEMBERS;
                return;
            }

            long totalScale;
            try {
                totalScale = Math.multiplyExact(parentScale, member.copiesPerCycle());
            } catch (ArithmeticException ignored) {
                failure = Status.ARITHMETIC_OVERFLOW;
                return;
            }

            final ResolvedMember resolved;
            try {
                resolved = resolver.resolve(member.pattern());
            } catch (RuntimeException ignored) {
                failure = Status.MEMBER_UNDECODABLE;
                return;
            }
            if (resolved == null) {
                failure = Status.MEMBER_UNDECODABLE;
                return;
            }
            if (resolved.executionMember()) {
                failure = Status.EXECUTION_MEMBER_REFERENCE;
                return;
            }
            if (resolved.nestedPayload() == null) {
                if (leaves.size() >= ClosedLoopPatternAnalyzer.MAX_MEMBERS) {
                    failure = Status.TOO_MANY_MEMBERS;
                    return;
                }
                leaves.add(new RawLeaf(
                        resolved.details(), member.pattern(), totalScale,
                        member.seedWaveCopies()));
                return;
            }

            if (depth >= MAX_NESTING_DEPTH) {
                failure = Status.NESTING_TOO_DEEP;
                return;
            }
            var payload = resolved.nestedPayload();
            if (!activePatternIds.add(payload.patternId())) {
                failure = Status.CYCLIC_REFERENCE;
                return;
            }
            try {
                for (var nestedMember : payload.memberPatterns()) {
                    expand(nestedMember, totalScale, depth + 1);
                    if (failure != null) return;
                }
            } finally {
                activePatternIds.remove(payload.patternId());
            }
        }
    }

    private record RawLeaf(
            IPatternDetails details,
            SourcePatternSnapshot snapshot,
            long totalCopies,
            long minimumSeedWaveCopies) {
    }

    private ClosedLoopPatternFlattener() {
    }
}
