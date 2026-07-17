package com.moakiee.ae2lt.logic.tianshu.loop;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.thunderbolt.ae2.overload.pattern.SourcePatternSnapshot;
import java.util.ArrayList;
import java.util.IdentityHashMap;
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
        INVALID_MARKING,
        NOT_BALANCED
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
                    || marked.copiesPerCycle() < 1
                    || marked.seedWaveCopies() < 1
                    || marked.seedWaveCopies() > marked.copiesPerCycle()
                    || marked.copiesPerCycle() % marked.seedWaveCopies() != 0L) {
                return new Result(Status.INVALID_MARKING, null);
            }
            details.add(marked.details());
            var analyzerMember = new ClosedLoopPatternAnalyzer.Member(
                    marked.details(), marked.copiesPerCycle(), marked.seedWaveCopies());
            analyzed.add(analyzerMember);
            byMember.put(analyzerMember, marked);
        }
        if (ClosedLoopPatternAnalyzer.seedWaveRepetitions(analyzed) <= 0L) {
            return new Result(Status.INVALID_MARKING, null);
        }

        var structure = ClosedLoopPatternAnalyzer.validateStructure(details);
        if (structure != ClosedLoopPatternAnalyzer.StructureStatus.VALID) {
            return new Result(Status.INVALID_MARKING, null);
        }

        var ordered = ClosedLoopPatternAnalyzer.analyzeBestOrder(analyzed, mainOutput);
        if (ordered == null) return new Result(Status.NOT_BALANCED, null);

        var stored = new ArrayList<ClosedLoopMemberPattern>(ordered.members().size());
        for (var member : ordered.members()) {
            var marked = byMember.get(member);
            if (marked == null || marked.copiesPerCycle() != member.copies()
                    || marked.seedWaveCopies() != member.seedWaveCopies()) {
                return new Result(Status.INVALID_MARKING, null);
            }
            stored.add(new ClosedLoopMemberPattern(
                    marked.snapshot(), member.copies(), member.seedWaveCopies()));
        }
        var analysis = ordered.analysis();
        if (!ClosedLoopPatternAnalyzer.hasInputSeedPerMember(
                ordered.members(), analysis.seeds())) {
            return new Result(Status.NOT_BALANCED, null);
        }
        var payload = new ClosedLoopPatternPayload(
                UUID.randomUUID(), 1L, stored, analysis.seeds(), analysis.externalInputs(),
                analysis.netOutputs(), executionSeedMultiplier, storedTaskMultiplier, true);
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
                    flattened.status() == ClosedLoopPatternFlattener.Status.MEMBER_UNDECODABLE
                            ? Status.MEMBER_UNDECODABLE
                            : Status.INVALID_MARKING,
                    null);
        }
        var marked = new ArrayList<MarkedMember>(flattened.members().size());
        for (var stored : flattened.members()) {
            marked.add(new MarkedMember(
                    stored.details(), stored.snapshot(),
                    stored.totalCopies(), stored.seedWaveCopies()));
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

    public record MarkedMember(
            IPatternDetails details,
            SourcePatternSnapshot snapshot,
            long copiesPerCycle,
            long seedWaveCopies) {
        public MarkedMember(
                IPatternDetails details,
                SourcePatternSnapshot snapshot,
                long copiesPerCycle) {
            this(details, snapshot, copiesPerCycle, copiesPerCycle);
        }

        public MarkedMember {
            Objects.requireNonNull(details, "details");
            Objects.requireNonNull(snapshot, "snapshot");
            if (copiesPerCycle < 1) throw new IllegalArgumentException("copies must be positive");
            if (seedWaveCopies < 1 || seedWaveCopies > copiesPerCycle
                    || copiesPerCycle % seedWaveCopies != 0L) {
                throw new IllegalArgumentException(
                        "copies must be an integer multiple of seed-wave copies");
            }
        }
    }

    public record Result(Status status, ClosedLoopPatternPayload payload) {
        public boolean valid() { return status == Status.VALID && payload != null; }
    }

    private ClosedLoopPatternAuthoringService() { }
}
