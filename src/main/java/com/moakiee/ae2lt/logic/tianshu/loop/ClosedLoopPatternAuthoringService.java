package com.moakiee.ae2lt.logic.tianshu.loop;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.thunderbolt.ae2.overload.pattern.SourcePatternSnapshot;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** Server-side authoring boundary: players mark only member patterns, quantities and main output. */
public final class ClosedLoopPatternAuthoringService {
    private static final ResourceLocation CLOSED_LOOP_PATTERN_ITEM_ID =
            ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "closed_loop_pattern");

    public enum Status {
        VALID,
        MEMBER_UNDECODABLE,
        TOO_MANY_MEMBERS,
        INVALID_MARKING,
        NON_MINIMAL_COPIES,
        NOT_BALANCED,
        INVALID_SEED_ROUTING
    }

    public static Result create(
            List<MarkedMember> markedMembers,
            AEKey mainOutput,
            int executionSeedMultiplier,
            int storedTaskMultiplier) {
        if (markedMembers == null || markedMembers.isEmpty()
                || markedMembers.size() > ClosedLoopPatternAnalyzer.MAX_MEMBERS
                || mainOutput == null
                || executionSeedMultiplier < 1
                || storedTaskMultiplier < 1) {
            return new Result(Status.INVALID_MARKING, null);
        }

        var details = new ArrayList<IPatternDetails>(markedMembers.size());
        var analyzed = new ArrayList<ClosedLoopPatternAnalyzer.Member>(markedMembers.size());
        // A flattened nested payload may legitimately contain the same decoded leaf more than
        // once. Track the analyzer member object itself so reordering remains lossless without
        // rejecting repeated delegates by identity.
        var byMember = new IdentityHashMap<ClosedLoopPatternAnalyzer.Member, MarkedMember>();
        for (var marked : markedMembers) {
            if (marked == null || marked.details() == null || marked.snapshot() == null
                    || marked.details() instanceof TianshuClosedLoopPatternDetails
                    || CLOSED_LOOP_PATTERN_ITEM_ID.equals(marked.snapshot().itemId())
                    || marked.copiesPerCycle() < 1) {
                return new Result(Status.INVALID_MARKING, null);
            }
            details.add(marked.details());
            var analyzerMember = new ClosedLoopPatternAnalyzer.Member(
                    marked.details(), marked.copiesPerCycle());
            analyzed.add(analyzerMember);
            byMember.put(analyzerMember, marked);
        }
        if (!ClosedLoopPatternAnalyzer.isMinimalIntegerRatio(
                analyzed.stream().mapToLong(ClosedLoopPatternAnalyzer.Member::copies).toArray())) {
            return new Result(Status.NON_MINIMAL_COPIES, null);
        }
        var structure = ClosedLoopPatternAnalyzer.validateStructure(details);
        if (structure != ClosedLoopPatternAnalyzer.StructureStatus.VALID) {
            return new Result(Status.INVALID_SEED_ROUTING, null);
        }

        var ordered = ClosedLoopPatternAnalyzer.analyzeBestOrder(analyzed, mainOutput);
        if (ordered == null) {
            var writtenAnalysis = ClosedLoopPatternAnalyzer.analyze(analyzed, mainOutput);
            if (writtenAnalysis != null && !ClosedLoopPatternAnalyzer.hasInputSeedPerMember(
                    analyzed, writtenAnalysis.seeds())) {
                return new Result(Status.INVALID_SEED_ROUTING, null);
            }
            return new Result(Status.NOT_BALANCED, null);
        }

        var stored = new ArrayList<ClosedLoopMemberPattern>(ordered.members().size());
        for (var member : ordered.members()) {
            var marked = byMember.get(member);
            if (marked == null || marked.copiesPerCycle() != member.copies()) {
                return new Result(Status.INVALID_MARKING, null);
            }
            stored.add(new ClosedLoopMemberPattern(
                    marked.snapshot(), member.copies()));
        }
        var analysis = ordered.analysis();
        if (!ClosedLoopPatternAnalyzer.hasInputSeedPerMember(
                ordered.members(), analysis.seeds())) {
            return new Result(Status.NOT_BALANCED, null);
        }
        var declaredOutputs = new ArrayList<appeng.api.stacks.GenericStack>(
                Math.min(ClosedLoopPatternPayload.MAX_NET_OUTPUTS, analysis.netOutputs().size()));
        for (var output : analysis.netOutputs()) {
            if (output.what().equals(mainOutput)) {
                declaredOutputs.add(output);
                break;
            }
        }
        for (var output : analysis.netOutputs()) {
            if (declaredOutputs.size() >= ClosedLoopPatternPayload.MAX_NET_OUTPUTS) break;
            if (!output.what().equals(mainOutput)) declaredOutputs.add(output);
        }
        var payload = new ClosedLoopPatternPayload(
                UUID.randomUUID(), 1L, stored, analysis.seeds(), analysis.externalInputs(),
                declaredOutputs, executionSeedMultiplier, storedTaskMultiplier, true);
        return new Result(Status.VALID, payload);
    }

    /** Compatibility overload for callers that predate stored-task capacity. */
    @Deprecated
    public static Result create(
            List<MarkedMember> markedMembers, AEKey mainOutput, int seedMultiplier) {
        return create(markedMembers, mainOutput, seedMultiplier, 1);
    }

    /** Rebuilds an auto-filled or manually edited draft through the canonical authoring path. */
    public static Result createFromDraft(
            List<ClosedLoopMemberPattern> draftMembers,
            AEKey mainOutput,
            int executionSeedMultiplier,
            int storedTaskMultiplier,
            Level level) {
        if (draftMembers == null || level == null) {
            return new Result(Status.INVALID_MARKING, null);
        }
        var flattened = ClosedLoopPatternFlattener.flatten(draftMembers, level);
        if (!flattened.valid()) {
            return new Result(
                    switch (flattened.status()) {
                        case MEMBER_UNDECODABLE -> Status.MEMBER_UNDECODABLE;
                        case TOO_MANY_MEMBERS -> Status.TOO_MANY_MEMBERS;
                        case NON_MINIMAL_COPIES -> Status.NON_MINIMAL_COPIES;
                        case INVALID_INPUT -> Status.INVALID_MARKING;
                        default -> Status.MEMBER_UNDECODABLE;
                    },
                    null);
        }
        var marked = new ArrayList<MarkedMember>(flattened.members().size());
        for (var stored : flattened.members()) {
            marked.add(new MarkedMember(
                    stored.details(), stored.snapshot(),
                    stored.totalCopies()));
        }
        return create(marked, mainOutput, executionSeedMultiplier, storedTaskMultiplier);
    }

    /** Compatibility overload for callers that predate stored-task capacity. */
    @Deprecated
    public static Result createFromDraft(
            List<ClosedLoopMemberPattern> draftMembers,
            AEKey mainOutput,
            int seedMultiplier,
            Level level) {
        return createFromDraft(draftMembers, mainOutput, seedMultiplier, 1, level);
    }

    /**
     * Authors a draft while declaring only the UI-marked output subset. The first key is the
     * primary request target and the remaining keys are secondary outputs.
     */
    public static Result createFromDraft(
            List<ClosedLoopMemberPattern> draftMembers,
            List<AEKey> declaredOutputs,
            int executionSeedMultiplier,
            int storedTaskMultiplier,
            Level level) {
        if (declaredOutputs == null || declaredOutputs.isEmpty()
                || declaredOutputs.size() > ClosedLoopPatternPayload.MAX_NET_OUTPUTS
                || new LinkedHashSet<>(declaredOutputs).size() != declaredOutputs.size()
                || declaredOutputs.stream().anyMatch(Objects::isNull)) {
            return new Result(Status.INVALID_MARKING, null);
        }
        var authored = createFromDraft(
                draftMembers, declaredOutputs.getFirst(), executionSeedMultiplier,
                storedTaskMultiplier, level);
        if (!authored.valid()) return authored;

        var analyzedOutputs = new LinkedHashMap<AEKey, appeng.api.stacks.GenericStack>();
        for (var output : authored.payload().netOutputs()) {
            analyzedOutputs.put(output.what(), output);
        }
        var selected = new ArrayList<appeng.api.stacks.GenericStack>(declaredOutputs.size());
        for (var key : declaredOutputs) {
            var output = analyzedOutputs.get(key);
            if (output == null) return new Result(Status.INVALID_MARKING, null);
            selected.add(output);
        }
        var payload = authored.payload();
        return new Result(Status.VALID, new ClosedLoopPatternPayload(
                payload.patternId(), payload.version(), payload.memberPatterns(), payload.seeds(),
                payload.externalInputs(), selected, payload.executionSeedMultiplier(),
                payload.storedTaskMultiplier(), payload.enabled()));
    }

    public record MarkedMember(
            IPatternDetails details,
            SourcePatternSnapshot snapshot,
            long copiesPerCycle) {
        public MarkedMember {
            Objects.requireNonNull(details, "details");
            Objects.requireNonNull(snapshot, "snapshot");
            if (copiesPerCycle < 1) throw new IllegalArgumentException("copies must be positive");
        }
    }

    public record Result(Status status, ClosedLoopPatternPayload payload) {
        public boolean valid() { return status == Status.VALID && payload != null; }
    }

    private ClosedLoopPatternAuthoringService() { }
}
