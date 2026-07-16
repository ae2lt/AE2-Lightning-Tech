package com.moakiee.ae2lt.logic.tianshu.loop;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.moakiee.thunderbolt.ae2.api.crafting.CraftingPatternDelegates;
import com.moakiee.thunderbolt.ae2.overload.pattern.OverloadedProviderOnlyPatternDetails;
import com.moakiee.thunderbolt.ae2.overload.pattern.SourcePatternSnapshot;
import java.util.ArrayList;
import java.util.HashMap;
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
    private static final int MAX_COMPOSITE_EXPANSIONS = 128;

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
        for (var resolved : resolveCandidates(
                crafting::getCraftingFor,
                template -> crafting.getCraftables(
                        candidate -> candidate != null
                                && template.dropSecondary().equals(candidate.dropSecondary())),
                requestedOutput)) {
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
        return resolveCandidates(patternsFor, ignored -> List.of(), requestedOutput);
    }

    /**
     * Variant-aware graph resolver. The second lookup enumerates craftable keys sharing an
     * input template's primary identity; it is consulted only for ID_ONLY overload inputs.
     */
    static List<ResolvedCandidate> resolveCandidates(
            Function<AEKey, ? extends Iterable<IPatternDetails>> patternsFor,
            Function<AEKey, ? extends Iterable<AEKey>> fuzzyCraftablesFor,
            AEKey requestedOutput) {
        if (patternsFor == null || fuzzyCraftablesFor == null || requestedOutput == null) {
            return List.of();
        }
        var patternCache = new HashMap<AEKey, List<IPatternDetails>>();
        Function<AEKey, Iterable<IPatternDetails>> cachedPatternsFor = key ->
                patternCache.computeIfAbsent(key, ignored -> copyPatterns(patternsFor.apply(key)));
        var fuzzyCache = new HashMap<AEKey, List<AEKey>>();
        Function<AEKey, Iterable<AEKey>> cachedFuzzyCraftablesFor = template ->
                fuzzyCache.computeIfAbsent(template.dropSecondary(), ignored ->
                        copyKeys(fuzzyCraftablesFor.apply(template)));
        return resolveCandidates(
                cachedPatternsFor, cachedFuzzyCraftablesFor, requestedOutput,
                new HashSet<>(), 0);
    }

    /**
     * Expands a directly closed candidate with independently closed loops that satisfy its
     * remaining external bridge inputs. This is what joins, for example, an A/B seed loop to a
     * C/D seed loop through a member that consumes both B and D.
     */
    private static List<ResolvedCandidate> resolveCandidates(
            Function<AEKey, ? extends Iterable<IPatternDetails>> patternsFor,
            Function<AEKey, ? extends Iterable<AEKey>> fuzzyCraftablesFor,
            AEKey requestedOutput,
            Set<AEKey> resolvingOutputs,
            int depth) {
        if (depth >= MAX_DEPTH || !resolvingOutputs.add(requestedOutput)) return List.of();
        try {
            var direct = resolveDirectCandidates(
                    patternsFor, fuzzyCraftablesFor, requestedOutput);
            if (direct.isEmpty()) return List.of();

            var result = new ArrayList<>(direct);
            var queue = new ArrayList<>(direct);
            var signatures = new HashSet<Set<IPatternDetails>>();
            for (var candidate : direct) signatures.add(Set.copyOf(candidate.members()));
            var nestedByOutput = new HashMap<AEKey, List<ResolvedCandidate>>();
            int queueIndex = 0;
            int expansions = 0;
            while (queueIndex < queue.size() && expansions < MAX_COMPOSITE_EXPANSIONS) {
                var base = queue.get(queueIndex++);
                if (base.members().size() >= ClosedLoopPatternAnalyzer.MAX_MEMBERS) continue;
                var externalKeys = new LinkedHashSet<AEKey>();
                for (var external : base.analysis().externalInputs()) {
                    if (external != null && external.what() != null && external.amount() > 0) {
                        externalKeys.add(external.what());
                    }
                }
                for (var externalKey : externalKeys) {
                    if (resolvingOutputs.contains(externalKey)) continue;
                    var nested = nestedByOutput.get(externalKey);
                    if (nested == null) {
                        nested = resolveCandidates(
                                patternsFor, fuzzyCraftablesFor, externalKey,
                                resolvingOutputs, depth + 1);
                        nestedByOutput.put(externalKey, nested);
                    }
                    for (var supplier : nested) {
                        var combined = combineCandidates(base, supplier, requestedOutput);
                        if (combined == null) continue;
                        var signature = Set.copyOf(combined.members());
                        if (!signatures.add(signature)) continue;
                        result.add(combined);
                        queue.add(combined);
                        expansions++;
                        if (expansions >= MAX_COMPOSITE_EXPANSIONS) break;
                    }
                    if (expansions >= MAX_COMPOSITE_EXPANSIONS) break;
                }
            }
            return List.copyOf(result);
        } finally {
            resolvingOutputs.remove(requestedOutput);
        }
    }

    private static List<ResolvedCandidate> resolveDirectCandidates(
            Function<AEKey, ? extends Iterable<IPatternDetails>> patternsFor,
            Function<AEKey, ? extends Iterable<AEKey>> fuzzyCraftablesFor,
            AEKey requestedOutput) {
        var result = new ArrayList<ResolvedCandidate>();
        var memberSetSignatures = new HashSet<Set<IPatternDetails>>();
        for (var root : safePatterns(patternsFor, requestedOutput)) {
            var rootInputs = inputRequirements(root);
            var rootAnchors = exactOutputs(root, requestedOutput);
            if (rootInputs == null || rootAnchors.isEmpty()) continue;
            var rootOutputs = outputs(root);

            var optionsByInput = new ArrayList<List<PathOption>>(rootInputs.size());
            for (var input : rootInputs) {
                var options = new ArrayList<PathOption>();
                options.add(new PathOption(List.of(), acceptsAny(input, rootOutputs)));
                var cycleAnchors = new LinkedHashSet<PatternOutput>(rootOutputs);
                for (var key : input.keys()) {
                    // A reusable state may close around the root input without ever becoming the
                    // requested output, e.g. A -> 2A followed by A -> C.
                    cycleAnchors.add(new PatternOutput(key, false));
                }
                for (var path : pathsBackToAnchor(
                        patternsFor, fuzzyCraftablesFor, input,
                        List.copyOf(cycleAnchors),
                        new HashSet<>(), 0)) {
                    options.add(new PathOption(path, true));
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
                        if (ClosedLoopPatternAnalyzer.validateStructure(memberList)
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

    private static ResolvedCandidate combineCandidates(
            ResolvedCandidate base,
            ResolvedCandidate supplier,
            AEKey requestedOutput) {
        var members = new LinkedHashSet<IPatternDetails>();
        members.addAll(base.members());
        int baseSize = members.size();
        members.addAll(supplier.members());
        if (members.size() == baseSize
                || members.size() > ClosedLoopPatternAnalyzer.MAX_MEMBERS) {
            return null;
        }
        var memberList = List.copyOf(members);
        var coefficientResult = ClosedLoopPatternAnalyzer.solveCoefficients(
                memberList, requestedOutput);
        if (!coefficientResult.solved()
                || ClosedLoopPatternAnalyzer.validateStructure(memberList)
                != ClosedLoopPatternAnalyzer.StructureStatus.VALID) {
            return null;
        }
        var ordered = analyzeBestOrder(
                memberList, coefficientResult.coefficients(), requestedOutput);
        return ordered != null
                ? new ResolvedCandidate(
                        ordered.members(), ordered.coefficients(), ordered.analysis())
                : null;
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
            Function<AEKey, ? extends Iterable<AEKey>> fuzzyCraftablesFor,
            InputRequirement needed,
            List<PatternOutput> anchors,
            Set<InputIdentity> visiting,
            int depth) {
        var identity = needed.identity();
        if (depth >= MAX_DEPTH || !visiting.add(identity)) return List.of();
        try {
            var result = new ArrayList<List<IPatternDetails>>();
            for (var pattern : patternsForRequirement(
                    patternsFor, fuzzyCraftablesFor, needed)) {
                if (!produces(pattern, needed)) continue;
                var inputs = inputRequirements(pattern);
                if (inputs == null) continue;
                if (inputs.stream().anyMatch(input -> acceptsAny(input, anchors))
                        || hasReturnedInput(pattern)) {
                    result.add(List.of(pattern));
                }
                for (var input : inputs) {
                    if (acceptsAny(input, anchors)) continue;
                    for (var tail : pathsBackToAnchor(
                            patternsFor, fuzzyCraftablesFor, input, anchors,
                            visiting, depth + 1)) {
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
            visiting.remove(identity);
        }
    }

    private static Iterable<IPatternDetails> patternsForRequirement(
            Function<AEKey, ? extends Iterable<IPatternDetails>> patternsFor,
            Function<AEKey, ? extends Iterable<AEKey>> fuzzyCraftablesFor,
            InputRequirement requirement) {
        var lookupKeys = new LinkedHashSet<AEKey>(requirement.keys());
        if (requirement.fuzzy()) {
            for (var template : requirement.keys()) {
                var variants = fuzzyCraftablesFor.apply(template);
                if (variants == null) continue;
                for (var variant : variants) {
                    if (variant != null && accepts(
                            requirement, new PatternOutput(variant, false))) {
                        lookupKeys.add(variant);
                    }
                }
            }
        }
        var result = new LinkedHashSet<IPatternDetails>();
        for (var key : lookupKeys) {
            for (var pattern : safePatterns(patternsFor, key)) {
                if (pattern != null) result.add(pattern);
            }
        }
        return List.copyOf(result);
    }

    private static Iterable<IPatternDetails> safePatterns(
            Function<AEKey, ? extends Iterable<IPatternDetails>> patternsFor, AEKey key) {
        var patterns = patternsFor.apply(key);
        return patterns != null ? patterns : List.of();
    }

    private static List<IPatternDetails> copyPatterns(Iterable<IPatternDetails> patterns) {
        if (patterns == null) return List.of();
        var result = new ArrayList<IPatternDetails>();
        for (var pattern : patterns) {
            if (pattern != null) result.add(pattern);
        }
        return List.copyOf(result);
    }

    private static List<AEKey> copyKeys(Iterable<AEKey> keys) {
        if (keys == null) return List.of();
        var result = new LinkedHashSet<AEKey>();
        for (var key : keys) {
            if (key != null) result.add(key);
        }
        return List.copyOf(result);
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

    private static List<InputRequirement> inputRequirements(IPatternDetails details) {
        if (details == null || details instanceof TianshuClosedLoopPatternDetails) return null;
        var inputs = details.getInputs();
        var providerDetails = CraftingPatternDelegates.forProviderLookup(details);
        var overload = providerDetails instanceof OverloadedProviderOnlyPatternDetails value
                ? value : null;
        var result = new ArrayList<InputRequirement>(inputs.length);
        for (int slot = 0; slot < inputs.length; slot++) {
            var input = inputs[slot];
            var possible = input.getPossibleInputs();
            if (possible.length == 0 || possible[0].what() == null) return null;
            var keys = new LinkedHashSet<AEKey>();
            for (var candidate : possible) {
                if (candidate != null && candidate.what() != null) keys.add(candidate.what());
            }
            if (keys.isEmpty()) return null;
            result.add(new InputRequirement(
                    possible, List.copyOf(keys), overload != null && overload.isFuzzyInput(slot)));
        }
        return List.copyOf(result);
    }

    private static boolean hasReturnedInput(IPatternDetails details) {
        var inputs = details.getInputs();
        var requirements = inputRequirements(details);
        if (requirements == null) return false;
        for (int slot = 0; slot < inputs.length; slot++) {
            var input = inputs[slot];
            var possible = input.getPossibleInputs();
            for (var candidate : possible) {
                if (candidate == null || candidate.what() == null) continue;
                var remaining = input.getRemainingKey(candidate.what());
                if (remaining != null && accepts(
                        requirements.get(slot), new PatternOutput(remaining, false))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean produces(IPatternDetails details, InputRequirement requirement) {
        if (details == null) return false;
        for (var output : outputs(details)) {
            if (accepts(requirement, output)) return true;
        }
        return false;
    }

    private static List<PatternOutput> exactOutputs(IPatternDetails details, AEKey key) {
        var result = new ArrayList<PatternOutput>();
        for (var output : outputs(details)) {
            if (key.equals(output.key())) result.add(output);
        }
        return List.copyOf(result);
    }

    private static List<PatternOutput> outputs(IPatternDetails details) {
        if (details == null) return List.of();
        var providerDetails = CraftingPatternDelegates.forProviderLookup(details);
        var overload = providerDetails instanceof OverloadedProviderOnlyPatternDetails value
                ? value : null;
        var outputs = details.getOutputs();
        var result = new ArrayList<PatternOutput>(outputs.size());
        for (int slot = 0; slot < outputs.size(); slot++) {
            var output = outputs.get(slot);
            if (output == null || output.what() == null || output.amount() <= 0) continue;
            result.add(new PatternOutput(
                    output.what(), overload != null && overload.isFuzzyOutput(slot)));
        }
        return List.copyOf(result);
    }

    private static boolean acceptsAny(
            InputRequirement requirement, List<PatternOutput> outputs) {
        for (var output : outputs) {
            if (accepts(requirement, output)) return true;
        }
        return false;
    }

    private static boolean accepts(InputRequirement requirement, PatternOutput output) {
        return ClosedLoopPatternAnalyzer.acceptsLoopOutput(
                requirement.possibleInputs(), requirement.fuzzy(),
                output.key(), output.fuzzy());
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

    private record PatternOutput(AEKey key, boolean fuzzy) { }

    private record InputIdentity(List<AEKey> keys, boolean fuzzy) {
        private InputIdentity {
            keys = List.copyOf(keys);
        }
    }

    private static final class InputRequirement {
        private final GenericStack[] possibleInputs;
        private final List<AEKey> keys;
        private final boolean fuzzy;

        private InputRequirement(GenericStack[] possibleInputs, List<AEKey> keys, boolean fuzzy) {
            this.possibleInputs = possibleInputs.clone();
            this.keys = List.copyOf(keys);
            this.fuzzy = fuzzy;
        }

        private GenericStack[] possibleInputs() {
            return possibleInputs;
        }

        private List<AEKey> keys() {
            return keys;
        }

        private boolean fuzzy() {
            return fuzzy;
        }

        private InputIdentity identity() {
            return new InputIdentity(keys, fuzzy);
        }
    }

    private ClosedLoopDiscoveryService() { }
}
