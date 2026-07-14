package com.moakiee.ae2lt.logic.tianshu.loop;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import com.moakiee.thunderbolt.ae2.overload.pattern.SourcePatternSnapshot;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.world.level.Level;

public final class ClosedLoopDiscoveryService {
    private static final int MAX_DEPTH = 8;
    private static final int MAX_PATHS_PER_INPUT = 32;
    private static final int MAX_MEMBER_COMBINATIONS = 128;

    public static List<ClosedLoopDiscoveryCandidate> discover(
            ICraftingService crafting, Level level, AEKey requestedOutput) {
        return discoverDetailed(crafting, level, requestedOutput).candidates();
    }

    public static DiscoveryResult discoverDetailed(
            ICraftingService crafting, Level level, AEKey requestedOutput) {
        if (crafting == null || level == null || requestedOutput == null) {
            return new DiscoveryResult(List.of(), false);
        }
        var result = new ArrayList<ClosedLoopDiscoveryCandidate>();
        var signatures = new HashSet<String>();
        boolean rejectedUndecodablePattern = false;
        for (var resolved : resolveCandidates(crafting::getCraftingFor, requestedOutput)) {
            var storedMembers = new ArrayList<ClosedLoopMemberPattern>(resolved.members().size());
            boolean valid = true;
            for (int i = 0; i < resolved.members().size(); i++) {
                var definition = resolved.members().get(i).getDefinition();
                if (definition == null) {
                    valid = false;
                    break;
                }
                var decoded = PatternDetailsHelper.decodePattern(definition, level);
                // Only persist members that participate in AE2's standard save/load decoder path.
                if (decoded == null) {
                    rejectedUndecodablePattern = true;
                    valid = false;
                    break;
                }
                storedMembers.add(new ClosedLoopMemberPattern(
                        SourcePatternSnapshot.fromItemStack(
                                definition.toStack(), level.registryAccess()),
                        resolved.coefficients()[i]));
            }
            if (!valid) continue;
            String signature = storedMembers.stream()
                    .map(member -> member.pattern().toTag() + "x" + member.copiesPerCycle())
                    .sorted().reduce("", (a, b) -> a + "|" + b);
            if (!signatures.add(signature)) continue;
            var analysis = resolved.analysis();
            result.add(new ClosedLoopDiscoveryCandidate(new ClosedLoopPatternPayload(
                    UUID.randomUUID(), 1L, storedMembers, analysis.seeds(), analysis.externalInputs(),
                    analysis.netOutputs(), 1, true)));
        }
        return new DiscoveryResult(result, rejectedUndecodablePattern);
    }

    public record DiscoveryResult(
            List<ClosedLoopDiscoveryCandidate> candidates,
            boolean rejectedUndecodablePattern) {
        public DiscoveryResult {
            candidates = List.copyOf(candidates);
        }
    }

    /** Pure graph portion, package-visible for deterministic unit tests. */
    static List<ResolvedCandidate> resolveCandidates(
            Function<AEKey, ? extends Iterable<IPatternDetails>> patternsFor,
            AEKey requestedOutput) {
        if (patternsFor == null || requestedOutput == null) return List.of();
        var result = new ArrayList<ResolvedCandidate>();
        var memberSetSignatures = new HashSet<Set<IPatternDetails>>();
        for (var root : safePatterns(patternsFor, requestedOutput)) {
            var rootInputs = deterministicInputs(root);
            if (!produces(root, requestedOutput) || rootInputs == null) continue;

            var optionsByInput = new ArrayList<List<PathOption>>(rootInputs.size());
            for (var input : rootInputs) {
                var options = new ArrayList<PathOption>();
                options.add(new PathOption(List.of(), input.equals(requestedOutput)));
                if (!input.equals(requestedOutput)) {
                    for (var path : pathsBackToAnchor(
                            patternsFor, input, requestedOutput, new HashSet<>(), 0)) {
                        options.add(new PathOption(path, true));
                    }
                }
                optionsByInput.add(List.copyOf(options));
            }

            var selected = new LinkedHashSet<IPatternDetails>();
            selected.add(root);
            var combinations = new int[] {0};
            enumerateMemberSets(optionsByInput, 0, selected, hasReturnedInput(root), combinations,
                    members -> {
                        if (members.size() > ClosedLoopPatternAnalyzer.MAX_MEMBERS
                                || !memberSetSignatures.add(Set.copyOf(members))) return;
                        var memberList = List.copyOf(members);
                        var coefficientResult = ClosedLoopPatternAnalyzer.solveCoefficients(
                                memberList, requestedOutput);
                        if (!coefficientResult.solved()) return;
                        if (ClosedLoopPatternAnalyzer.validateMinimalStructure(memberList)
                                != ClosedLoopPatternAnalyzer.StructureStatus.VALID) return;
                        long[] coefficients = coefficientResult.coefficients();
                        var ordered = analyzeBestOrder(memberList, coefficients, requestedOutput);
                        if (ordered != null) {
                            result.add(new ResolvedCandidate(
                                    ordered.members(), ordered.coefficients(), ordered.analysis()));
                        }
                    });
        }
        return List.copyOf(result);
    }

    private static void enumerateMemberSets(
            List<List<PathOption>> optionsByInput,
            int index,
            LinkedHashSet<IPatternDetails> selected,
            boolean closes,
            int[] combinations,
            java.util.function.Consumer<LinkedHashSet<IPatternDetails>> sink) {
        if (combinations[0] >= MAX_MEMBER_COMBINATIONS) return;
        if (index == optionsByInput.size()) {
            combinations[0]++;
            if (closes) sink.accept(new LinkedHashSet<>(selected));
            return;
        }
        for (var option : optionsByInput.get(index)) {
            var added = new ArrayList<IPatternDetails>();
            for (var member : option.members()) {
                if (selected.add(member)) added.add(member);
            }
            if (selected.size() <= ClosedLoopPatternAnalyzer.MAX_MEMBERS) {
                enumerateMemberSets(optionsByInput, index + 1, selected,
                        closes || option.closes(), combinations, sink);
            }
            for (int i = added.size() - 1; i >= 0; i--) selected.remove(added.get(i));
            if (combinations[0] >= MAX_MEMBER_COMBINATIONS) return;
        }
    }

    private static List<List<IPatternDetails>> pathsBackToAnchor(
            Function<AEKey, ? extends Iterable<IPatternDetails>> patternsFor,
            AEKey needed,
            AEKey anchor,
            Set<AEKey> visiting,
            int depth) {
        if (depth >= MAX_DEPTH || !visiting.add(needed)) return List.of();
        try {
            var result = new ArrayList<List<IPatternDetails>>();
            for (var pattern : safePatterns(patternsFor, needed)) {
                if (!produces(pattern, needed)) continue;
                var inputs = deterministicInputs(pattern);
                if (inputs == null) continue;
                if (inputs.contains(anchor) || hasReturnedInput(pattern)) {
                    result.add(List.of(pattern));
                }
                for (var input : inputs) {
                    if (input.equals(anchor)) continue;
                    for (var tail : pathsBackToAnchor(
                            patternsFor, input, anchor, visiting, depth + 1)) {
                        var path = new ArrayList<IPatternDetails>(tail.size() + 1);
                        path.add(pattern);
                        path.addAll(tail);
                        result.add(List.copyOf(path));
                        if (result.size() >= MAX_PATHS_PER_INPUT) return List.copyOf(result);
                    }
                }
            }
            return List.copyOf(result);
        } finally {
            visiting.remove(needed);
        }
    }

    private static Iterable<IPatternDetails> safePatterns(
            Function<AEKey, ? extends Iterable<IPatternDetails>> patternsFor, AEKey key) {
        var patterns = patternsFor.apply(key);
        return patterns != null ? patterns : List.of();
    }

    private static OrderedCandidate analyzeBestOrder(
            List<IPatternDetails> members, long[] coefficients, AEKey output) {
        var analyzed = new ArrayList<ClosedLoopPatternAnalyzer.Member>(members.size());
        for (int i = 0; i < members.size(); i++) {
            analyzed.add(new ClosedLoopPatternAnalyzer.Member(members.get(i), coefficients[i]));
        }
        var ordered = ClosedLoopPatternAnalyzer.analyzeBestOrder(analyzed, output);
        if (ordered == null) return null;
        var orderedDetails = new ArrayList<IPatternDetails>(ordered.members().size());
        var orderedCoefficients = new long[ordered.members().size()];
        for (int i = 0; i < ordered.members().size(); i++) {
            orderedDetails.add(ordered.members().get(i).details());
            orderedCoefficients[i] = ordered.members().get(i).copies();
        }
        return new OrderedCandidate(
                List.copyOf(orderedDetails), orderedCoefficients, ordered.analysis());
    }

    private static List<AEKey> deterministicInputs(IPatternDetails details) {
        if (details == null || details instanceof TianshuClosedLoopPatternDetails) return null;
        var result = new ArrayList<AEKey>();
        for (var input : details.getInputs()) {
            var possible = input.getPossibleInputs();
            if (possible.length == 0 || possible[0].what() == null) return null;
            result.add(possible[0].what());
        }
        return result;
    }

    private static boolean hasReturnedInput(IPatternDetails details) {
        var inputs = details.getInputs();
        for (var input : inputs) {
            var possible = input.getPossibleInputs();
            if (possible.length == 1 && possible[0].what() != null
                    && possible[0].what().equals(input.getRemainingKey(possible[0].what()))) return true;
        }
        return false;
    }

    private static boolean produces(IPatternDetails details, AEKey key) {
        if (details == null) return false;
        for (var output : details.getOutputs()) {
            if (output != null && key.equals(output.what()) && output.amount() > 0) return true;
        }
        return false;
    }

    static record ResolvedCandidate(
            List<IPatternDetails> members,
            long[] coefficients,
            ClosedLoopAnalysis analysis) { }

    private record OrderedCandidate(
            List<IPatternDetails> members,
            long[] coefficients,
            ClosedLoopAnalysis analysis) { }

    private record PathOption(List<IPatternDetails> members, boolean closes) { }

    private ClosedLoopDiscoveryService() { }
}
