package com.moakiee.ae2lt.crafting.runtime;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.moakiee.thunderbolt.core.crafting.pattern.IProviderLookupPattern;
import com.moakiee.thunderbolt.core.crafting.pattern.IBatchExecutionPattern;
import com.moakiee.thunderbolt.core.crafting.loop.IPlannedSeedSlotPattern;
import com.moakiee.thunderbolt.core.crafting.loop.IPrioritizedCraftingTask;
import com.moakiee.thunderbolt.core.crafting.loop.ISeedPreservingCraftingTask;
import com.moakiee.thunderbolt.core.crafting.support.CraftingPatternDelegates;
import com.moakiee.thunderbolt.core.crafting.batch.SharedBatchInputPattern;
import com.moakiee.ae2lt.overload.runtime.pattern.OverloadedProviderOnlyPatternDetails;
import com.moakiee.thunderbolt.core.crafting.loop.CraftingTaskPersistenceDefinition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.UUID;

/**
 * Execution-only wrapper for one closed-loop member task. Provider lookup and pushing still use
 * the delegate pattern. Reusable state is accounted to the consumer that will use it, rather than
 * to the pattern that happened to produce it: {@link #inputSeed} is debited from this consumer and
 * {@link #outputSeedCredits} is credited to the fixed downstream consumers. Outputs not routed by
 * {@code outputSeedCredits} remain ordinary public outputs.
 */
public final class ExecuteLoopPattern implements IPatternDetails, IProviderLookupPattern,
        IBatchExecutionPattern,
        IPrioritizedCraftingTask, ISeedPreservingCraftingTask,
        CraftingTaskPersistenceDefinition, SharedBatchInputPattern {
    /** Runtime account used by fuzzy-output metadata for a safely shareable cycle boundary. */
    public static final UUID SHARED_SEED_ACCOUNT_ID =
            UUID.fromString("ae2ae2ae-51ed-4acc-8000-000000000001");

    private final IPatternDetails delegate;
    private final UUID seedConsumerId;
    private final KeyCounter initialSeed;
    private final KeyCounter inputSeed;
    private final Map<UUID, KeyCounter> outputSeedCredits;
    private final Map<UUID, KeyCounter> sharedOutputSeedCredits;
    private final IInput[] executionInputs;
    // All inputs below are immutable, so every per-slot / per-planned-key query is precomputed
    // once here instead of re-walking the delegate chain on each hot-path call.
    private final Map<Integer, AEKey> plannedSlots;
    private final boolean[] fuzzyInputs;
    private final boolean[] seedSlots;
    private final Map<AEKey, SeedVariantRule> plannedSeedRules;
    private final int dispatchPriority;
    private final int dispatchOrder;
    /** Cached because the seed counters and routed-credit maps are immutable after construction. */
    private final int hashCode;

    public ExecuteLoopPattern(
            IPatternDetails delegate,
            UUID seedConsumerId,
            KeyCounter initialSeed,
            KeyCounter inputSeed,
            Map<UUID, KeyCounter> outputSeedCredits) {
        this(delegate, seedConsumerId, initialSeed, inputSeed, outputSeedCredits, Map.of());
    }

    public ExecuteLoopPattern(
            IPatternDetails delegate,
            UUID seedConsumerId,
            KeyCounter initialSeed,
            KeyCounter inputSeed,
            Map<UUID, KeyCounter> outputSeedCredits,
            Map<UUID, KeyCounter> sharedOutputSeedCredits) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        if (!(delegate instanceof ISeedPreservingCraftingTask)) {
            throw new IllegalArgumentException("loop execution delegate must identify its seed group");
        }
        if (!(delegate instanceof CraftingTaskPersistenceDefinition)) {
            throw new IllegalArgumentException("loop execution delegate must be persistable");
        }
        this.seedConsumerId = Objects.requireNonNull(seedConsumerId, "seedConsumerId");
        this.initialSeed = copy(initialSeed);
        this.inputSeed = copy(inputSeed);
        this.outputSeedCredits = copyCredits(outputSeedCredits);
        this.sharedOutputSeedCredits = copyCredits(sharedOutputSeedCredits);
        this.plannedSlots = snapshotPlannedSeedSlots(delegate);
        var sourceInputs = delegate.getInputs();
        this.fuzzyInputs = computeFuzzyInputs(delegate, sourceInputs.length);
        this.executionInputs = constrainRepeatedPlannedSeedSlots(sourceInputs);
        this.seedSlots = computeSeedSlots();
        this.plannedSeedRules = computePlannedSeedRules();
        // Host implementations return constants; the wheel's priority comparator queries these
        // for every bucket entry on every poll.
        this.dispatchPriority = delegate instanceof IPrioritizedCraftingTask prioritized
                ? prioritized.dispatchPriority() : 0;
        this.dispatchOrder = delegate instanceof IPrioritizedCraftingTask prioritized
                ? prioritized.dispatchOrder() : 0;
        this.hashCode = computeHashCode();
    }

    private static Map<Integer, AEKey> snapshotPlannedSeedSlots(IPatternDetails delegate) {
        if (!(delegate instanceof IPlannedSeedSlotPattern mapped)) return Map.of();
        var source = mapped.plannedSeedInputSlots();
        if (source == null || source.isEmpty()) return Map.of();
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static boolean[] computeFuzzyInputs(IPatternDetails delegate, int slotCount) {
        var result = new boolean[slotCount];
        if (CraftingPatternDelegates.forProviderLookup(delegate)
                instanceof OverloadedProviderOnlyPatternDetails overload) {
            for (int slot = 0; slot < slotCount; slot++) {
                result[slot] = overload.isFuzzyInput(slot);
            }
        }
        return result;
    }

    private boolean[] computeSeedSlots() {
        var result = new boolean[executionInputs.length];
        for (int slot = 0; slot < result.length; slot++) {
            result[slot] = computeIsSeedSlot(slot);
        }
        return result;
    }

    private Map<AEKey, SeedVariantRule> computePlannedSeedRules() {
        var result = new LinkedHashMap<AEKey, SeedVariantRule>();
        for (var planned : inputSeed) {
            result.put(planned.getKey(), computeSeedVariantRule(planned.getKey()));
        }
        return Collections.unmodifiableMap(result);
    }

    public IPatternDetails delegate() { return delegate; }

    @Override
    public IPatternDetails batchExecutionPattern() { return delegate; }

    @Override
    public boolean isSharedBatchInput(int slot, AEKey concreteKey) {
        return delegate instanceof SharedBatchInputPattern shared
                && shared.isSharedBatchInput(slot, concreteKey);
    }

    @Override
    public long sharedBatchOutputAmount(AEKey outputKey) {
        return delegate instanceof SharedBatchInputPattern shared
                ? shared.sharedBatchOutputAmount(outputKey) : 0L;
    }
    public UUID seedConsumerId() { return seedConsumerId; }
    public KeyCounter initialSeed() { return copy(initialSeed); }
    public KeyCounter inputSeed() { return copy(inputSeed); }
    public Map<UUID, KeyCounter> outputSeedCredits() {
        return copyCredits(outputSeedCredits);
    }
    /** Credits that close a full safe cycle and may re-enter the cross-loop shared seed account. */
    public Map<UUID, KeyCounter> sharedOutputSeedCredits() {
        return copyCredits(sharedOutputSeedCredits);
    }
    /** Runtime account allocations, with all safe boundary credits collapsed into one pool. */
    public Map<UUID, KeyCounter> runtimeOutputSeedCredits() {
        var result = new LinkedHashMap<UUID, KeyCounter>();
        for (var entry : outputSeedCredits.entrySet()) {
            result.put(entry.getKey(), copy(entry.getValue()));
        }
        var shared = new KeyCounter();
        for (var credit : sharedOutputSeedCredits.values()) shared.addAll(credit);
        if (!shared.isEmpty()) result.put(SHARED_SEED_ACCOUNT_ID, shared);
        return Collections.unmodifiableMap(result);
    }

    /** Aggregate internal output amount, retained for output-slot classification. */
    public KeyCounter outputSeed() {
        var result = new KeyCounter();
        for (var credit : outputSeedCredits.values()) result.addAll(credit);
        for (var credit : sharedOutputSeedCredits.values()) result.addAll(credit);
        return result;
    }

    /** Fixed consumer allocations for one planned internal output key. */
    public Map<UUID, Long> outputSeedConsumers(AEKey expectedKey) {
        if (expectedKey == null) return Map.of();
        var result = new LinkedHashMap<UUID, Long>();
        for (var credit : outputSeedCredits.entrySet()) {
            long amount = credit.getValue().get(expectedKey);
            if (amount > 0) result.put(credit.getKey(), amount);
        }
        long shared = 0L;
        for (var credit : sharedOutputSeedCredits.values()) {
            shared = saturatingAdd(shared, credit.get(expectedKey));
        }
        if (shared > 0) result.put(SHARED_SEED_ACCOUNT_ID, shared);
        return Collections.unmodifiableMap(result);
    }

    /** Whether this concrete key belongs to one of this task's reusable-seed input slots. */
    public boolean isInputSeedKey(AEKey key) {
        if (key == null) return false;
        if (inputSeed.get(key) > 0) return true;
        for (var planned : inputSeed) {
            if (seedVariantRule(planned.getKey()).accepts(key)) return true;
        }
        return false;
    }

    /** Whether one concrete variant can discharge this consumer's planned seed obligation. */
    public boolean acceptsInputSeedVariant(AEKey planned, AEKey actual) {
        if (planned == null || actual == null) return false;
        return seedVariantRule(planned).accepts(actual);
    }

    /** Serializable acceptance rule for one logical reusable input. */
    public SeedVariantRule seedVariantRule(AEKey planned) {
        if (planned == null) return new SeedVariantRule(Set.of(), Set.of());
        var cached = plannedSeedRules.get(planned);
        if (cached != null) return cached;
        return computeSeedVariantRule(planned);
    }

    private SeedVariantRule computeSeedVariantRule(AEKey planned) {
        SeedVariantRule combined = null;
        var inputs = executionInputs;
        var plannedSlots = plannedSeedInputSlots();
        for (int slot = 0; slot < inputs.length; slot++) {
            if (!plannedSlots.isEmpty()
                    && !planned.equals(plannedSlots.get(slot))) continue;
            if (!isSeedSlot(slot)) continue;
            var slotRule = seedVariantRuleForSlot(slot, planned, inputs[slot]);
            if (slotRule == null) continue;
            combined = combined == null ? slotRule : combined.intersect(slotRule);
        }
        return combined != null
                ? combined : new SeedVariantRule(Set.of(planned), Set.of());
    }

    /** Reusable input units one ordinary delegate copy may take from this concrete key. */
    public long inputSeedAmountFor(AEKey key) {
        if (key == null) return 0L;
        long exact = inputSeed.get(key);
        if (exact > 0) return exact;
        long result = 0L;
        for (var planned : inputSeed) {
            if (seedVariantRule(planned.getKey()).accepts(key)) {
                result = saturatingAdd(result, planned.getLongValue());
            }
        }
        return result;
    }

    /** Fuzzy seed slots must report the exact key physically extracted by the CPU. */
    public boolean requiresActualSeedKeyTracking() {
        var inputs = executionInputs;
        for (int slot = 0; slot < inputs.length; slot++) {
            if (isSeedSlot(slot)
                    && (isFuzzyInput(slot) || inputs[slot].getPossibleInputs().length > 1)) {
                return true;
            }
        }
        return false;
    }

    /** Extracts only the reusable-seed portion from AE2's resolved per-slot input holders. */
    public KeyCounter actualInputSeed(KeyCounter[] inputHolders) {
        var result = new KeyCounter();
        for (var use : resolveActualInputSeedUses(inputHolders).uses()) {
            result.add(use.actual(), use.amount());
        }
        return result;
    }

    /** Planned-to-concrete reusable inputs selected by AE2 for one pushed pattern copy. */
    public List<ActualSeedUse> actualInputSeedUses(KeyCounter[] inputHolders) {
        return resolveActualInputSeedUses(inputHolders).uses();
    }

    /**
     * Resolves every physical unit extracted from reusable-input slots. {@code complete=false}
     * makes amount-changing or otherwise ambiguous substitutions fail closed before provider push.
     */
    public ActualSeedResolution resolveActualInputSeedUses(KeyCounter[] inputHolders) {
        if (inputHolders == null) return new ActualSeedResolution(List.of(), false);
        var result = new ArrayList<ActualSeedUse>();
        var remainingPlanned = new LinkedHashMap<AEKey, Long>();
        for (var planned : inputSeed) {
            if (planned.getLongValue() > 0) {
                remainingPlanned.put(planned.getKey(), planned.getLongValue());
            }
        }
        int slots = Math.min(executionInputs.length, inputHolders.length);
        boolean complete = inputHolders.length >= executionInputs.length;
        for (int slot = 0; slot < slots; slot++) {
            if (!isSeedSlot(slot) || inputHolders[slot] == null) continue;
            for (var actual : inputHolders[slot]) {
                long amount = actual.getLongValue();
                if (amount <= 0) continue;
                while (amount > 0) {
                    var planned = plannedSeedForSlot(slot, actual.getKey(), remainingPlanned);
                    if (planned == null) {
                        complete = false;
                        break;
                    }
                    long used = Math.min(amount, remainingPlanned.getOrDefault(planned, 0L));
                    if (used <= 0) {
                        complete = false;
                        break;
                    }
                    var input = executionInputs[slot];
                    result.add(new ActualSeedUse(
                            planned,
                            actual.getKey(),
                            input.getRemainingKey(planned),
                            input.getRemainingKey(actual.getKey()),
                            used,
                            remainderOperations(slot, input, actual.getKey(), used)));
                    amount -= used;
                    long left = remainingPlanned.get(planned) - used;
                    if (left > 0) remainingPlanned.put(planned, left);
                    else remainingPlanned.remove(planned);
                }
            }
        }
        if (!remainingPlanned.isEmpty()) complete = false;
        return new ActualSeedResolution(result, complete);
    }

    @Override public IPatternDetails providerLookupPattern() { return delegate; }
    @Override public AEItemKey craftingTaskPersistenceDefinition() {
        return ((CraftingTaskPersistenceDefinition) delegate).craftingTaskPersistenceDefinition();
    }
    @Override public int dispatchPriority() { return dispatchPriority; }
    @Override public int dispatchOrder() { return dispatchOrder; }
    @Override public UUID reusableSeedGroupId() {
        return ((ISeedPreservingCraftingTask) delegate).reusableSeedGroupId();
    }
    @Override public Set<AEKey> reusableSeedCycleKeys() {
        return ((ISeedPreservingCraftingTask) delegate).reusableSeedCycleKeys();
    }
    @Override public boolean hasSingleSeedInputPerMember() {
        return ((ISeedPreservingCraftingTask) delegate).hasSingleSeedInputPerMember();
    }
    @Override public AEItemKey getDefinition() { return delegate.getDefinition(); }
    @Override public IInput[] getInputs() { return executionInputs.clone(); }
    @Override public List<GenericStack> getOutputs() { return delegate.getOutputs(); }
    @Override public boolean supportsPushInputsToExternalInventory() {
        return delegate.supportsPushInputsToExternalInventory();
    }
    @Override public void pushInputsToExternalInventory(
            KeyCounter[] inputHolder, PatternInputSink inputSink) {
        delegate.pushInputsToExternalInventory(inputHolder, inputSink);
    }

    /**
     * Execution wrappers are equal only when every source field that affects seed accounting is
     * equal. This deliberately permits safely identical tasks to merge in AE2's task maps while
     * keeping tasks for different consumers or routed credits separate.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ExecuteLoopPattern other) || hashCode != other.hashCode) return false;
        return delegate.equals(other.delegate)
                && seedConsumerId.equals(other.seedConsumerId)
                && counterEquals(initialSeed, other.initialSeed)
                && counterEquals(inputSeed, other.inputSeed)
                && creditMapsEqual(outputSeedCredits, other.outputSeedCredits)
                && creditMapsEqual(sharedOutputSeedCredits, other.sharedOutputSeedCredits);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    private int computeHashCode() {
        int result = delegate.hashCode();
        result = 31 * result + seedConsumerId.hashCode();
        result = 31 * result + counterHashCode(initialSeed);
        result = 31 * result + counterHashCode(inputSeed);
        result = 31 * result + creditMapHashCode(outputSeedCredits);
        result = 31 * result + creditMapHashCode(sharedOutputSeedCredits);
        return result;
    }

    private static boolean counterEquals(KeyCounter left, KeyCounter right) {
        if (left == right) return true;
        int leftEntries = 0;
        for (var entry : left) {
            long amount = entry.getLongValue();
            if (amount == 0L) continue;
            leftEntries++;
            if (right.get(entry.getKey()) != amount) return false;
        }
        int rightEntries = 0;
        for (var entry : right) {
            if (entry.getLongValue() != 0L) rightEntries++;
        }
        return leftEntries == rightEntries;
    }

    private static int counterHashCode(KeyCounter counter) {
        int result = 0;
        for (var entry : counter) {
            long amount = entry.getLongValue();
            if (amount == 0L) continue;
            result += entry.getKey().hashCode() ^ Long.hashCode(amount);
        }
        return result;
    }

    private static boolean creditMapsEqual(
            Map<UUID, KeyCounter> left, Map<UUID, KeyCounter> right) {
        if (left == right) return true;
        if (left.size() != right.size()) return false;
        for (var entry : left.entrySet()) {
            var other = right.get(entry.getKey());
            if (other == null || !counterEquals(entry.getValue(), other)) return false;
        }
        return true;
    }

    private static int creditMapHashCode(Map<UUID, KeyCounter> credits) {
        int result = 0;
        for (var entry : credits.entrySet()) {
            result += entry.getKey().hashCode() ^ counterHashCode(entry.getValue());
        }
        return result;
    }

    private static KeyCounter copy(KeyCounter source) {
        var result = new KeyCounter();
        if (source != null) result.addAll(source);
        return result;
    }

    private static Map<UUID, KeyCounter> copyCredits(Map<UUID, KeyCounter> source) {
        var result = new LinkedHashMap<UUID, KeyCounter>();
        if (source != null) {
            for (var entry : source.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null
                        || entry.getValue().isEmpty()) continue;
                result.put(entry.getKey(), copy(entry.getValue()));
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private boolean isSeedSlot(int slot) {
        return slot >= 0 && slot < seedSlots.length && seedSlots[slot];
    }

    private boolean computeIsSeedSlot(int slot) {
        var inputs = executionInputs;
        if (slot < 0 || slot >= inputs.length) return false;
        var plannedSlots = plannedSeedInputSlots();
        if (!plannedSlots.isEmpty()) {
            var planned = plannedSlots.get(slot);
            return planned != null && inputSeed.get(planned) > 0;
        }
        boolean fuzzy = isFuzzyInput(slot);
        for (var possible : inputs[slot].getPossibleInputs()) {
            if (possible.what() == null) continue;
            for (var expected : inputSeed) {
                if (matches(expected.getKey(), possible.what(), fuzzy)) return true;
            }
        }
        return false;
    }

    private boolean isFuzzyInput(int slot) {
        return slot >= 0 && slot < fuzzyInputs.length && fuzzyInputs[slot];
    }

    private AEKey plannedSeedForSlot(
            int slot, AEKey actual, Map<AEKey, Long> remainingPlanned) {
        var plannedSlots = plannedSeedInputSlots();
        if (!plannedSlots.isEmpty()) {
            var planned = plannedSlots.get(slot);
            if (planned == null || remainingPlanned.getOrDefault(planned, 0L) <= 0) return null;
            var rule = seedVariantRuleForSlot(slot, planned, executionInputs[slot]);
            return rule != null && rule.accepts(actual) ? planned : null;
        }
        AEKey fuzzyMatch = null;
        AEKey alternativeMatch = null;
        boolean fuzzy = isFuzzyInput(slot);
        var possible = executionInputs[slot].getPossibleInputs();
        boolean acceptsActual = false;
        for (var option : possible) {
            if (option.what() != null && matches(option.what(), actual, fuzzy)) {
                acceptsActual = true;
                break;
            }
        }
        for (var planned : remainingPlanned.entrySet()) {
            if (planned.getValue() <= 0) continue;
            boolean belongsToSlot = false;
            for (var option : possible) {
                if (option.what() != null && matches(planned.getKey(), option.what(), fuzzy)) {
                    belongsToSlot = true;
                    break;
                }
            }
            if (!belongsToSlot) continue;
            if (planned.getKey().equals(actual)) return planned.getKey();
            if (matches(planned.getKey(), actual, fuzzy)) fuzzyMatch = planned.getKey();
            // AE2 substitution inputs may expose several exact alternatives without declaring the
            // slot fuzzy. The analyzer selects one planned loop key, while extraction may legally
            // resolve another option from the same slot. Keep the debit attached to the planned
            // consumer account and re-key it to the concrete option just like an ID_ONLY input.
            if (acceptsActual && alternativeMatch == null) alternativeMatch = planned.getKey();
        }
        return fuzzyMatch != null ? fuzzyMatch : alternativeMatch;
    }

    private Map<Integer, AEKey> plannedSeedInputSlots() {
        return plannedSlots;
    }

    /**
     * AE2 resolves input slots greedily. If several slots consume the same logical seed, allowing
     * a variant accepted by only one slot can strand a later strict slot even when the aggregate
     * inventory looked feasible. Restrict those slots to their common safe variant set.
     */
    private IInput[] constrainRepeatedPlannedSeedSlots(IInput[] source) {
        var result = source.clone();
        var plannedSlots = plannedSeedInputSlots();
        if (plannedSlots.isEmpty()) return result;
        var slotsByPlanned = new LinkedHashMap<AEKey, List<Integer>>();
        for (var entry : plannedSlots.entrySet()) {
            if (entry.getKey() < 0 || entry.getKey() >= source.length || entry.getValue() == null) {
                throw new IllegalArgumentException("planned reusable-seed slot is invalid");
            }
            slotsByPlanned.computeIfAbsent(entry.getValue(), ignored -> new ArrayList<>())
                    .add(entry.getKey());
        }
        for (var group : slotsByPlanned.entrySet()) {
            if (group.getValue().size() < 2) continue;
            SeedVariantRule common = null;
            for (int slot : group.getValue()) {
                var rule = seedVariantRuleForSlot(slot, group.getKey(), source[slot]);
                if (rule == null) {
                    throw new IllegalArgumentException(
                            "planned reusable seed is not accepted by its mapped slot");
                }
                common = common == null ? rule : common.intersect(rule);
            }
            if (common == null || !common.accepts(group.getKey())) {
                throw new IllegalArgumentException(
                        "repeated reusable-seed slots have no common safe state");
            }
            for (int slot : group.getValue()) {
                result[slot] = new VariantConstrainedInput(source[slot], common);
            }
        }
        return result;
    }

    private SeedVariantRule seedVariantRuleForSlot(int slot, AEKey planned, IInput input) {
        boolean fuzzy = isFuzzyInput(slot);
        boolean containsPlanned = false;
        var exact = new LinkedHashSet<AEKey>();
        var fuzzyIdentities = new LinkedHashSet<AEKey>();
        exact.add(planned);
        for (var possible : input.getPossibleInputs()) {
            if (possible.what() == null) continue;
            containsPlanned |= matches(planned, possible.what(), fuzzy);
            exact.add(possible.what());
            if (fuzzy) fuzzyIdentities.add(possible.what().dropSecondary());
        }
        if (!containsPlanned) return null;
        var result = new SeedVariantRule(exact, fuzzyIdentities);
        if (input instanceof VariantConstrainedInput constrained) {
            result = result.intersect(constrained.allowed);
        }
        return result;
    }

    private long remainderOperations(int slot, IInput input, AEKey actual, long usedAmount) {
        if (input.getRemainingKey(actual) == null) return 0L;
        long templateAmount = -1L;
        for (var possible : input.getPossibleInputs()) {
            if (possible.what() == null || !matches(
                    possible.what(), actual, isFuzzyInput(slot))) continue;
            if (possible.amount() <= 0) return 0L;
            if (templateAmount >= 0L && templateAmount != possible.amount()) return 0L;
            templateAmount = possible.amount();
        }
        if (templateAmount <= 0L || usedAmount % templateAmount != 0L) return 0L;
        return usedAmount / templateAmount;
    }

    private static boolean matches(AEKey expected, AEKey actual, boolean fuzzy) {
        return expected.equals(actual)
                || (fuzzy && expected.dropSecondary().equals(actual.dropSecondary()));
    }

    private static long saturatingAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    public record ActualSeedUse(
            AEKey planned,
            AEKey actual,
            AEKey plannedRemainder,
            AEKey actualRemainder,
            long amount,
            long remainderAmount) {
        public ActualSeedUse {
            Objects.requireNonNull(planned, "planned");
            Objects.requireNonNull(actual, "actual");
            if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
            if (remainderAmount < 0) {
                throw new IllegalArgumentException("remainderAmount must not be negative");
            }
        }
    }

    public record ActualSeedResolution(List<ActualSeedUse> uses, boolean complete) {
        public ActualSeedResolution {
            uses = List.copyOf(Objects.requireNonNull(uses, "uses"));
        }
    }

    public record SeedVariantRule(Set<AEKey> exactVariants, Set<AEKey> fuzzyIdentities) {
        public SeedVariantRule {
            exactVariants = Collections.unmodifiableSet(new LinkedHashSet<>(
                    Objects.requireNonNull(exactVariants, "exactVariants")));
            fuzzyIdentities = Collections.unmodifiableSet(new LinkedHashSet<>(
                    Objects.requireNonNull(fuzzyIdentities, "fuzzyIdentities")));
        }

        public boolean accepts(AEKey actual) {
            if (actual == null) return false;
            return exactVariants.contains(actual)
                    || fuzzyIdentities.contains(actual.dropSecondary());
        }

        public SeedVariantRule merge(SeedVariantRule other) {
            Objects.requireNonNull(other, "other");
            var exact = new LinkedHashSet<>(exactVariants);
            exact.addAll(other.exactVariants);
            var fuzzy = new LinkedHashSet<>(fuzzyIdentities);
            fuzzy.addAll(other.fuzzyIdentities);
            return new SeedVariantRule(exact, fuzzy);
        }

        /** Variants accepted by every contributing slot for the same planned key. */
        public SeedVariantRule intersect(SeedVariantRule other) {
            Objects.requireNonNull(other, "other");
            var exact = new LinkedHashSet<AEKey>();
            var candidates = new LinkedHashSet<AEKey>(exactVariants);
            candidates.addAll(other.exactVariants);
            for (var candidate : candidates) {
                if (accepts(candidate) && other.accepts(candidate)) exact.add(candidate);
            }
            var fuzzy = new LinkedHashSet<AEKey>(fuzzyIdentities);
            fuzzy.retainAll(other.fuzzyIdentities);
            return new SeedVariantRule(exact, fuzzy);
        }
    }

    private static final class VariantConstrainedInput implements IInput {
        private final IInput source;
        private final SeedVariantRule allowed;
        private final GenericStack[] possible;

        private VariantConstrainedInput(IInput source, SeedVariantRule allowed) {
            this.source = Objects.requireNonNull(source, "source");
            this.allowed = Objects.requireNonNull(allowed, "allowed");
            var filtered = new ArrayList<GenericStack>();
            for (var candidate : source.getPossibleInputs()) {
                if (candidate.what() != null && allowed.accepts(candidate.what())) {
                    filtered.add(candidate);
                }
            }
            if (filtered.isEmpty()) {
                throw new IllegalArgumentException(
                        "reusable-seed slot has no safely executable variant");
            }
            this.possible = filtered.toArray(GenericStack[]::new);
        }

        @Override public GenericStack[] getPossibleInputs() { return possible.clone(); }
        @Override public long getMultiplier() { return source.getMultiplier(); }
        @Override public boolean isValid(AEKey input, net.minecraft.world.level.Level level) {
            return allowed.accepts(input) && source.isValid(input, level);
        }
        @Override public AEKey getRemainingKey(AEKey template) {
            return allowed.accepts(template) ? source.getRemainingKey(template) : null;
        }
    }
}
