package com.moakiee.ae2lt.logic.tianshu.loop;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEKey;
import com.moakiee.thunderbolt.ae2.overload.pattern.SourcePatternSnapshot;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.world.level.Level;

/** Server-side authoring boundary: players mark only member patterns, quantities and main output. */
public final class ClosedLoopPatternAuthoringService {
    public enum Status {
        VALID,
        MEMBER_UNDECODABLE,
        INVALID_MARKING,
        NOT_CONNECTED,
        NOT_MINIMAL,
        NOT_BALANCED
    }

    public static Result create(
            List<MarkedMember> markedMembers, AEKey mainOutput, int seedMultiplier) {
        if (markedMembers == null || markedMembers.isEmpty()
                || markedMembers.size() > ClosedLoopPatternAnalyzer.MAX_MEMBERS
                || mainOutput == null || seedMultiplier < 1) {
            return new Result(Status.INVALID_MARKING, null);
        }

        var details = new ArrayList<IPatternDetails>(markedMembers.size());
        var analyzed = new ArrayList<ClosedLoopPatternAnalyzer.Member>(markedMembers.size());
        var byDetails = new IdentityHashMap<IPatternDetails, MarkedMember>();
        for (var marked : markedMembers) {
            if (marked == null || marked.details() == null || marked.snapshot() == null
                    || marked.copiesPerCycle() < 1
                    || byDetails.put(marked.details(), marked) != null) {
                return new Result(Status.INVALID_MARKING, null);
            }
            details.add(marked.details());
            analyzed.add(new ClosedLoopPatternAnalyzer.Member(
                    marked.details(), marked.copiesPerCycle()));
        }

        var structure = ClosedLoopPatternAnalyzer.validateMinimalStructure(details);
        if (structure != ClosedLoopPatternAnalyzer.StructureStatus.VALID) {
            return new Result(switch (structure) {
                case NOT_CONNECTED -> Status.NOT_CONNECTED;
                case NOT_MINIMAL -> Status.NOT_MINIMAL;
                case INVALID -> Status.INVALID_MARKING;
                case VALID -> throw new IllegalStateException("unreachable");
            }, null);
        }

        var ordered = ClosedLoopPatternAnalyzer.analyzeBestOrder(analyzed, mainOutput);
        if (ordered == null) return new Result(Status.NOT_BALANCED, null);

        var stored = new ArrayList<ClosedLoopMemberPattern>(ordered.members().size());
        for (var member : ordered.members()) {
            var marked = byDetails.get(member.details());
            if (marked == null || marked.copiesPerCycle() != member.copies()) {
                return new Result(Status.INVALID_MARKING, null);
            }
            stored.add(new ClosedLoopMemberPattern(marked.snapshot(), member.copies()));
        }
        var analysis = ordered.analysis();
        if (ClosedLoopPatternAnalyzer.deriveMemberFlows(
                ordered.members(), analysis.seeds()).size() != ordered.members().size()) {
            return new Result(Status.NOT_BALANCED, null);
        }
        var payload = new ClosedLoopPatternPayload(
                UUID.randomUUID(), 1L, stored, analysis.seeds(), analysis.externalInputs(),
                analysis.netOutputs(), seedMultiplier, true);
        return new Result(Status.VALID, payload);
    }

    /** Rebuilds an auto-filled or manually edited draft through the canonical authoring path. */
    public static Result createFromDraft(
            List<ClosedLoopMemberPattern> draftMembers,
            AEKey mainOutput,
            int seedMultiplier,
            Level level) {
        if (draftMembers == null || level == null) {
            return new Result(Status.INVALID_MARKING, null);
        }
        var marked = new ArrayList<MarkedMember>(draftMembers.size());
        for (var stored : draftMembers) {
            var details = PatternDetailsHelper.decodePattern(
                    stored.pattern().toItemStack(level.registryAccess()), level);
            if (details == null) return new Result(Status.MEMBER_UNDECODABLE, null);
            marked.add(new MarkedMember(details, stored.pattern(), stored.copiesPerCycle()));
        }
        return create(marked, mainOutput, seedMultiplier);
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
