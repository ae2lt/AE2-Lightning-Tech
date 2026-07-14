package com.moakiee.ae2lt.logic.tianshu.loop;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.crafting.IPatternDetails;
import com.moakiee.thunderbolt.ae2.crafting.PatternFiringExpander;
import com.moakiee.thunderbolt.core.planner.Sat;
import com.moakiee.thunderbolt.ae2.timewheel.TimeWheelPoolRestrictedPattern;
import com.moakiee.thunderbolt.ae2.timewheel.TimeWheelCraftingCpuPoolHost;
import com.moakiee.thunderbolt.ae2.timewheel.ReusableSeedPattern;
import com.moakiee.ae2lt.blockentity.TianshuSupercomputerPortBlockEntity;
import java.util.UUID;

public final class Ae2ClosedLoopPatternDetails
        implements TianshuClosedLoopPatternDetails, PatternFiringExpander,
        TimeWheelPoolRestrictedPattern, ReusableSeedPattern {
    private final AEItemKey definition;
    private final ClosedLoopPatternPayload payload;
    private final IInput[] inputs;
    private final List<ExpandedMember> members;
    private final UUID owningTianshuId;
    private final Map<AEKey, Long> availableSeedSnapshot;

    public Ae2ClosedLoopPatternDetails(AEItemKey definition, ClosedLoopPatternPayload payload, Level level) {
        this(definition, payload, level, null, Map.of());
    }

    public Ae2ClosedLoopPatternDetails(AEItemKey definition, ClosedLoopPatternPayload payload,
                                       Level level, UUID owningTianshuId) {
        this(definition, payload, level, owningTianshuId, Map.of());
    }

    public Ae2ClosedLoopPatternDetails(AEItemKey definition, ClosedLoopPatternPayload payload,
                                       Level level, UUID owningTianshuId,
                                       Map<AEKey, Long> availableSeedSnapshot) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.payload = Objects.requireNonNull(payload, "payload");
        this.owningTianshuId = owningTianshuId;
        this.availableSeedSnapshot = Map.copyOf(availableSeedSnapshot);
        var allInputs = new ArrayList<GenericStack>(payload.seeds().size() + payload.externalInputs().size());
        for (var seed : payload.seeds()) allInputs.add(seed);
        for (var input : payload.externalInputs()) allInputs.add(input);
        inputs = new IInput[allInputs.size()];
        int slot = 0;
        for (var seed : payload.seeds()) inputs[slot++] = new ExactInput(seed, true);
        for (var input : payload.externalInputs()) inputs[slot++] = new ExactInput(input, false);

        var decodedMembers = new ArrayList<ExpandedMember>(payload.memberPatterns().size());
        var seedAmounts = new java.util.LinkedHashMap<AEKey, Long>();
        for (var seed : payload.seeds()) seedAmounts.merge(seed.what(), seed.amount(), Sat::add);
        int memberIndex = 0;
        for (var member : payload.memberPatterns()) {
            var details = PatternDetailsHelper.decodePattern(
                    member.pattern().toItemStack(level.registryAccess()), level);
            if (details == null || details instanceof TianshuClosedLoopPatternDetails) {
                throw new IllegalArgumentException("closed-loop member pattern is no longer decodable");
            }
            var item = (com.moakiee.ae2lt.item.ClosedLoopPatternItem) definition.getItem();
            var persistenceDefinition = AEItemKey.of(item.createExecutionMemberStack(
                    payload, memberIndex, level.registryAccess()));
            if (persistenceDefinition == null) {
                throw new IllegalArgumentException("closed-loop member persistence key is unavailable");
            }
            decodedMembers.add(new ExpandedMember(
                    ClosedLoopExpandedPatternDetails.wrap(
                            details, seedAmounts, payload.memberPatterns().size() == 1,
                            persistenceDefinition, memberIndex),
                    member.copiesPerCycle()));
            memberIndex++;
        }
        members = List.copyOf(decodedMembers);
    }

    @Override
    public AEItemKey getDefinition() {
        return definition;
    }

    @Override
    public IInput[] getInputs() {
        return inputs.clone();
    }

    @Override
    public List<GenericStack> getOutputs() {
        return payload.netOutputs();
    }

    @Override
    public ClosedLoopPatternPayload closedLoopPayload() {
        return payload;
    }

    @Override
    public Map<IPatternDetails, Long> expandPatternFirings(long macroFirings) {
        var result = new LinkedHashMap<IPatternDetails, Long>();
        for (var member : members) {
            result.merge(member.details(), Sat.mul(macroFirings, member.copiesPerCycle()), Sat::add);
        }
        return Map.copyOf(result);
    }

    @Override
    public boolean acceptsTimeWheelPool(TimeWheelCraftingCpuPoolHost host) {
        return owningTianshuId != null
                && host instanceof TianshuSupercomputerPortBlockEntity port
                && owningTianshuId.equals(port.getTianshuId());
    }

    @Override
    public Map<AEKey, Long> reusableSeedRequirements() {
        var result = new LinkedHashMap<AEKey, Long>();
        for (var seed : payload.seeds()) {
            result.merge(seed.what(), seed.amount(), Sat::add);
        }
        return Map.copyOf(result);
    }

    @Override
    public Map<AEKey, Long> maximumReusableSeedRequirements() {
        var result = new LinkedHashMap<AEKey, Long>();
        for (var seed : payload.seeds()) {
            result.merge(seed.what(), Sat.mul(seed.amount(), payload.seedMultiplier()), Sat::add);
        }
        return Map.copyOf(result);
    }

    @Override
    public Map<AEKey, Long> availableReusableSeedSnapshot() {
        return availableSeedSnapshot;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Ae2ClosedLoopPatternDetails other && definition.equals(other.definition);
    }

    @Override
    public int hashCode() {
        return definition.hashCode();
    }

    private static final class ExactInput implements IInput {
        private final GenericStack[] possibleInputs;

        private final boolean returned;

        private ExactInput(GenericStack input, boolean returned) {
            possibleInputs = new GenericStack[] { input };
            this.returned = returned;
        }

        @Override
        public GenericStack[] getPossibleInputs() {
            return possibleInputs.clone();
        }

        @Override
        public long getMultiplier() {
            return 1;
        }

        @Override
        public boolean isValid(AEKey input, Level level) {
            return possibleInputs[0].what().equals(input);
        }

        @Override
        public @Nullable AEKey getRemainingKey(AEKey template) {
            return returned && possibleInputs[0].what().equals(template) ? template : null;
        }
    }

    private record ExpandedMember(IPatternDetails details, long copiesPerCycle) {
    }
}
