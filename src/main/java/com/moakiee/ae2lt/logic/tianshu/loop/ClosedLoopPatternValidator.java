package com.moakiee.ae2lt.logic.tianshu.loop;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.moakiee.thunderbolt.core.planner.Sat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.Level;

/** Server-side validation for encoded or uploaded closed-loop declarations. */
public final class ClosedLoopPatternValidator {
    public static ClosedLoopValidationResult validate(ClosedLoopPatternPayload payload, Level level) {
        if (payload == null || level == null) {
            return invalid(ClosedLoopValidationResult.Status.MEMBER_UNDECODABLE);
        }

        var members = new ArrayList<ClosedLoopPatternAnalyzer.Member>(payload.memberPatterns().size());
        for (var stored : payload.memberPatterns()) {
            var details = PatternDetailsHelper.decodePattern(
                    stored.pattern().toItemStack(level.registryAccess()), level);
            if (details == null) {
                return invalid(ClosedLoopValidationResult.Status.MEMBER_UNDECODABLE);
            }
            if (details instanceof TianshuClosedLoopPatternDetails) {
                return invalid(ClosedLoopValidationResult.Status.MEMBER_IS_CLOSED_LOOP);
            }
            members.add(new ClosedLoopPatternAnalyzer.Member(details, stored.copiesPerCycle()));
        }

        var structure = ClosedLoopPatternAnalyzer.validateMinimalStructure(
                members.stream().map(ClosedLoopPatternAnalyzer.Member::details).toList());
        if (structure != ClosedLoopPatternAnalyzer.StructureStatus.VALID) {
            return invalid(switch (structure) {
                case NOT_CONNECTED -> ClosedLoopValidationResult.Status.MEMBERS_NOT_CONNECTED;
                case NOT_MINIMAL -> ClosedLoopValidationResult.Status.MEMBERS_NOT_MINIMAL;
                case INVALID -> ClosedLoopValidationResult.Status.STRUCTURE_CHECK_FAILED;
                case VALID -> throw new IllegalStateException("unreachable");
            });
        }

        for (var declaredOutput : payload.netOutputs()) {
            var analysis = ClosedLoopPatternAnalyzer.analyze(members, declaredOutput.what());
            if (analysis == null) continue;
            if (sameStacks(payload.seeds(), analysis.seeds())
                    && sameStacks(payload.externalInputs(), analysis.externalInputs())
                    && sameStacks(payload.netOutputs(), analysis.netOutputs())) {
                return new ClosedLoopValidationResult(ClosedLoopValidationResult.Status.VALID, analysis);
            }
            return new ClosedLoopValidationResult(
                    ClosedLoopValidationResult.Status.DECLARATION_MISMATCH, analysis);
        }
        return invalid(ClosedLoopValidationResult.Status.NO_VALID_NET_OUTPUT);
    }

    private static boolean sameStacks(List<GenericStack> left, List<GenericStack> right) {
        return amounts(left).equals(amounts(right));
    }

    private static Map<AEKey, Long> amounts(List<GenericStack> stacks) {
        var result = new LinkedHashMap<AEKey, Long>();
        for (var stack : stacks) result.merge(stack.what(), stack.amount(), Sat::add);
        return Map.copyOf(result);
    }

    private static ClosedLoopValidationResult invalid(ClosedLoopValidationResult.Status status) {
        return new ClosedLoopValidationResult(status, null);
    }

    private ClosedLoopPatternValidator() { }
}
