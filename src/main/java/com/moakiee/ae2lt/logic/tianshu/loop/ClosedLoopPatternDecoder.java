package com.moakiee.ae2lt.logic.tianshu.loop;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.IPatternDetailsDecoder;
import appeng.api.stacks.AEItemKey;
import com.moakiee.ae2lt.item.ClosedLoopPatternItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public final class ClosedLoopPatternDecoder implements IPatternDetailsDecoder {
    public static final ClosedLoopPatternDecoder INSTANCE = new ClosedLoopPatternDecoder();

    private ClosedLoopPatternDecoder() {
    }

    @Override
    public boolean isEncodedPattern(ItemStack stack) {
        return stack.getItem() instanceof ClosedLoopPatternItem item && item.hasPayload(stack);
    }

    @Override
    public @Nullable IPatternDetails decodePattern(AEItemKey what, Level level) {
        if (what == null || !(what.getItem() instanceof ClosedLoopPatternItem item)) return null;
        var stack = what.toStack();
        var payload = item.readPayload(stack, level).orElse(null);
        if (payload == null) return null;
        try {
            int executionMember = item.readExecutionMember(stack);
            if (executionMember >= 0) {
                if (executionMember >= payload.memberPatterns().size()) return null;
                var decodedMembers = new java.util.ArrayList<IPatternDetails>(
                        payload.memberPatterns().size());
                for (var stored : payload.memberPatterns()) {
                    var decoded = appeng.api.crafting.PatternDetailsHelper.decodePattern(
                            stored.pattern().toItemStack(level.registryAccess()), level);
                    if (decoded == null || decoded instanceof TianshuClosedLoopPatternDetails) return null;
                    decodedMembers.add(decoded);
                }
                var delegate = decodedMembers.get(executionMember);
                var seedAmounts = new java.util.LinkedHashMap<appeng.api.stacks.AEKey, Long>();
                for (var seed : payload.seeds()) seedAmounts.merge(
                        seed.what(), seed.amount(), com.moakiee.thunderbolt.core.planner.Sat::add);
                var cycleKeys = ClosedLoopCycleKeys.analyze(decodedMembers, seedAmounts.keySet());
                var analyzedMembers = new java.util.ArrayList<ClosedLoopPatternAnalyzer.Member>(
                        decodedMembers.size());
                for (int i = 0; i < decodedMembers.size(); i++) {
                    analyzedMembers.add(new ClosedLoopPatternAnalyzer.Member(
                            decodedMembers.get(i), payload.memberPatterns().get(i).copiesPerCycle()));
                }
                var memberFlows = ClosedLoopPatternAnalyzer.deriveMemberFlows(
                        analyzedMembers, payload.seeds());
                if (memberFlows.size() != decodedMembers.size()) return null;
                Ae2ClosedLoopPatternDetails.validateFuzzyOutputSeedConsumers(
                        analyzedMembers, memberFlows);
                var acceptedVariants = new java.util.LinkedHashMap<
                        appeng.api.stacks.AEKey, java.util.Set<appeng.api.stacks.AEKey>>();
                var fuzzySeeds = new java.util.LinkedHashSet<appeng.api.stacks.AEKey>();
                Ae2ClosedLoopPatternDetails.collectAcceptedSeedVariants(
                        decodedMembers, memberFlows, seedAmounts.keySet(),
                        acceptedVariants, fuzzySeeds);
                boolean singleSeedInputPerMember =
                        Ae2ClosedLoopPatternDetails.isSharedSeedPoolSafe(
                                ClosedLoopPatternAnalyzer.hasSingleSeedInputPerMember(memberFlows),
                                seedAmounts.keySet(), acceptedVariants, fuzzySeeds);
                var memberFlow = memberFlows.get(executionMember);
                return ClosedLoopExpandedPatternDetails.wrap(
                        delegate,
                        Ae2ClosedLoopPatternDetails.memberSeedAmounts(
                                seedAmounts, memberFlow.inputSeed().keySet()),
                        cycleKeys, payload.patternId(),
                        singleSeedInputPerMember,
                        memberFlow.inputSeedBySlot(),
                        payload.memberPatterns().size() == 1, what, executionMember);
            }
            if (!payload.enabled()) return null;
            return new Ae2ClosedLoopPatternDetails(what, payload, level);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
