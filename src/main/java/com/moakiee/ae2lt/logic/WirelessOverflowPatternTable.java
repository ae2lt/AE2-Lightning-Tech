package com.moakiee.ae2lt.logic;

import java.util.HashSet;
import java.util.List;
import java.util.function.Supplier;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap;

import org.jetbrains.annotations.Nullable;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import com.moakiee.thunderbolt.ae2.overload.model.MatchMode;
import com.moakiee.thunderbolt.ae2.overload.pattern.OverloadedProviderOnlyPatternDetails;
import com.moakiee.thunderbolt.ae2.overload.pattern.OverloadPatternDetails;

/** Runtime pattern IDs and compact-overflow shape validation. */
final class WirelessOverflowPatternTable {
    private static final int MAX_PATTERN_IDS = 0xFFFF;

    private final Object2IntOpenCustomHashMap<IPatternDetails> byPattern =
            new Object2IntOpenCustomHashMap<>(
                    CanonicalPatternMaps.strategy());
    private final Int2ObjectOpenHashMap<IPatternDetails> byId =
            new Int2ObjectOpenHashMap<>();
    private int nextId;

    WirelessOverflowPatternTable() {
        byPattern.defaultReturnValue(-1);
    }

    short intern(
            IPatternDetails pattern,
            Supplier<? extends Iterable<? extends PatternReference>> liveReferences) {
        int id = byPattern.getInt(pattern);
        if (id >= 0) {
            return (short) id;
        }

        if (byId.size() >= MAX_PATTERN_IDS) {
            compact(liveReferences.get());
        }

        for (int attempts = 0; attempts <= MAX_PATTERN_IDS; attempts++) {
            id = allocateId();
            if (!byId.containsKey(id)) {
                put(id, pattern);
                return (short) id;
            }
        }

        compact(liveReferences.get());
        id = allocateId();
        put(id, pattern);
        return (short) id;
    }

    @Nullable
    IPatternDetails get(int unsignedId) {
        return byId.get(unsignedId);
    }

    void restore(int id, IPatternDetails pattern) {
        put(id, pattern);
        nextId = Math.max(nextId, (id + 1) & MAX_PATTERN_IDS);
    }

    void clear() {
        byPattern.clear();
        byId.clear();
        nextId = 0;
    }

    private void compact(Iterable<? extends PatternReference> liveReferences) {
        var previous = new Int2ObjectOpenHashMap<IPatternDetails>(byId);
        clear();

        for (var reference : liveReferences) {
            if (!reference.usesPatternDefinition()) {
                continue;
            }
            var pattern = previous.get(reference.unsignedPatternId());
            if (pattern == null) {
                continue;
            }
            int id = allocateId();
            reference.setPatternId((short) id);
            put(id, pattern);
        }
    }

    private int allocateId() {
        int id = nextId++ & MAX_PATTERN_IDS;
        if (nextId > MAX_PATTERN_IDS) {
            nextId = 0;
        }
        return id;
    }

    private void put(int id, IPatternDetails pattern) {
        byPattern.put(pattern, id);
        byId.put(id, pattern);
    }

    static boolean isCompactEligible(IPatternDetails pattern) {
        OverloadPatternDetails overloadDetails =
                pattern instanceof OverloadedProviderOnlyPatternDetails overload
                        ? overload.overloadPatternDetailsView()
                        : null;
        var seen = new HashSet<AEKey>();
        var inputs = pattern.getInputs();
        for (int i = 0; i < inputs.length; i++) {
            if (overloadDetails != null
                    && overloadDetails.inputMode(i) != MatchMode.STRICT) {
                return false;
            }
            var possible = inputs[i].getPossibleInputs();
            if (possible.length != 1 || !seen.add(possible[0].what())) {
                return false;
            }
        }
        return true;
    }

    static int findSlotIndex(IPatternDetails.IInput[] inputs, AEKey key) {
        for (int i = 0; i < inputs.length; i++) {
            var possible = inputs[i].getPossibleInputs();
            if (possible.length == 1 && possible[0].what().equals(key)) {
                return i;
            }
        }
        return -1;
    }

    static boolean verifySequentialOverflow(
            IPatternDetails.IInput[] inputs,
            int stuckIndex,
            List<GenericStack> overflow) {
        if (stuckIndex < 0 || stuckIndex >= inputs.length
                || overflow.size() != inputs.length - stuckIndex) {
            return false;
        }

        for (int i = 0; i < overflow.size(); i++) {
            var stack = overflow.get(i);
            var possible = inputs[stuckIndex + i].getPossibleInputs();
            if (possible.length != 1 || !possible[0].what().equals(stack.what())) {
                return false;
            }
            long fullAmount = inputAmount(inputs[stuckIndex + i]);
            if (i == 0) {
                if (stack.amount() <= 0 || stack.amount() > fullAmount) {
                    return false;
                }
            } else if (stack.amount() != fullAmount) {
                return false;
            }
        }
        return true;
    }

    static long inputAmount(IPatternDetails.IInput input) {
        var possible = input.getPossibleInputs();
        return possible.length == 0
                ? 0L
                : possible[0].amount() * input.getMultiplier();
    }

    interface PatternReference {
        boolean usesPatternDefinition();

        int unsignedPatternId();

        void setPatternId(short patternId);
    }
}
