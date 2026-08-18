package com.moakiee.ae2lt.logic.tianshu.loop;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.IPatternDetailsDecoder;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import com.moakiee.ae2lt.item.ClosedLoopPatternItem;
import com.moakiee.thunderbolt.ae2.timewheel.ReusableSeedPattern;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.RegistryAccess;

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
                var memberDecoding = decodeMembers(payload, level);
                if (!memberDecoding.valid()) return null;
                var decodedMembers = memberDecoding.members();
                var delegate = decodedMembers.get(executionMember);
                var seedAmounts = new java.util.LinkedHashMap<appeng.api.stacks.AEKey, Long>();
                for (var seed : payload.seeds()) seedAmounts.merge(
                        seed.what(), seed.amount(), com.moakiee.thunderbolt.core.planner.Sat::add);
                var cycleKeys = ClosedLoopCycleKeys.analyze(decodedMembers, seedAmounts.keySet());
                var analyzedMembers = new java.util.ArrayList<ClosedLoopPatternAnalyzer.Member>(
                        decodedMembers.size());
                for (int i = 0; i < decodedMembers.size(); i++) {
                    analyzedMembers.add(new ClosedLoopPatternAnalyzer.Member(
                            decodedMembers.get(i),
                            payload.memberPatterns().get(i).copiesPerCycle()));
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
                var rootDefinition = AEItemKey.of(
                        item.createStack(payload, level.registryAccess()));
                if (rootDefinition == null) return null;
                boolean singleSeedInputPerMember =
                        Ae2ClosedLoopPatternDetails.isSharedSeedPoolSafe(
                                ClosedLoopPatternAnalyzer.hasSingleSeedInputPerMember(memberFlows),
                                seedAmounts.keySet(), acceptedVariants, fuzzySeeds);
                var memberFlow = memberFlows.get(executionMember);
                return ClosedLoopExpandedPatternDetails.wrap(
                        delegate,
                        Ae2ClosedLoopPatternDetails.memberSeedAmounts(
                                seedAmounts, memberFlow.inputSeed().keySet()),
                        cycleKeys, ClosedLoopPatternIdentity.runtimeGroupId(
                                rootDefinition, level.registryAccess()),
                        singleSeedInputPerMember,
                        memberFlow.inputSeedBySlot(),
                        payload.memberPatterns().size() == 1, what, executionMember);
            }
            if (!payload.enabled()) return null;
            var decoded = decodePayload(payload, level);
            return decoded.valid()
                    ? decoded.createDetails(what, level, null, ignored -> Map.of())
                    : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Override
    public @Nullable IPatternDetails decodePattern(ItemStack stack, Level level, boolean trySort) {
        if (stack == null || stack.isEmpty()) return null;
        var key = AEItemKey.of(stack);
        return key != null ? decodePattern(key, level) : null;
    }

    /** Decodes and validates all ordinary members once for every downstream closed-loop stage. */
    public static DecodedPayload decodePayload(ClosedLoopPatternPayload payload, Level level) {
        if (payload == null || level == null) {
            return DecodedPayload.invalid(ClosedLoopValidationResult.Status.MEMBER_UNDECODABLE);
        }
        var memberDecoding = decodeMembers(payload, level);
        if (!memberDecoding.valid()) {
            return DecodedPayload.invalid(memberDecoding.failure());
        }
        return new DecodedPayload(
                payload,
                ClosedLoopPatternValidator.validateDecoded(payload, memberDecoding.members()),
                memberDecoding.members());
    }

    private static MemberDecoding decodeMembers(ClosedLoopPatternPayload payload, Level level) {
        var decodedMembers = new java.util.ArrayList<IPatternDetails>(
                payload.memberPatterns().size());
        for (var stored : payload.memberPatterns()) {
            final ItemStack memberStack;
            try {
                memberStack = stored.pattern().toItemStack();
            } catch (RuntimeException ignored) {
                return MemberDecoding.invalid(
                        ClosedLoopValidationResult.Status.MEMBER_UNDECODABLE);
            }
            if (memberStack.getItem() instanceof ClosedLoopPatternItem) {
                return MemberDecoding.invalid(
                        ClosedLoopValidationResult.Status.MEMBER_IS_CLOSED_LOOP);
            }
            final IPatternDetails decoded;
            try {
                decoded = appeng.api.crafting.PatternDetailsHelper.decodePattern(memberStack, level);
            } catch (RuntimeException ignored) {
                return MemberDecoding.invalid(
                        ClosedLoopValidationResult.Status.MEMBER_UNDECODABLE);
            }
            if (decoded == null) {
                return MemberDecoding.invalid(
                        ClosedLoopValidationResult.Status.MEMBER_UNDECODABLE);
            }
            if (decoded instanceof TianshuClosedLoopPatternDetails) {
                return MemberDecoding.invalid(
                        ClosedLoopValidationResult.Status.MEMBER_IS_CLOSED_LOOP);
            }
            decodedMembers.add(decoded);
        }
        return new MemberDecoding(List.copyOf(decodedMembers), null);
    }

    public record DecodedPayload(
            @Nullable ClosedLoopPatternPayload payload,
            ClosedLoopValidationResult validation,
            List<IPatternDetails> members) {
        public DecodedPayload {
            validation = Objects.requireNonNull(validation, "validation");
            members = List.copyOf(members);
        }

        public boolean valid() {
            return validation.valid();
        }

        public Ae2ClosedLoopPatternDetails createDetails(
                AEItemKey definition,
                Level level,
                UUID owningTianshuId,
                Function<ReusableSeedPattern, Map<AEKey, Long>> availableSeedSnapshotFactory) {
            if (!valid() || payload == null) {
                throw new IllegalStateException("cannot create details from an invalid closed-loop payload");
            }
            return new Ae2ClosedLoopPatternDetails(
                    definition, payload, level, owningTianshuId,
                    availableSeedSnapshotFactory, members);
        }

        private static DecodedPayload invalid(ClosedLoopValidationResult.Status status) {
            return new DecodedPayload(
                    null, ClosedLoopPatternValidator.invalid(status), List.of());
        }
    }

    private record MemberDecoding(
            List<IPatternDetails> members,
            @Nullable ClosedLoopValidationResult.Status failure) {
        private boolean valid() {
            return failure == null;
        }

        private static MemberDecoding invalid(ClosedLoopValidationResult.Status status) {
            return new MemberDecoding(List.of(), status);
        }
    }
}

