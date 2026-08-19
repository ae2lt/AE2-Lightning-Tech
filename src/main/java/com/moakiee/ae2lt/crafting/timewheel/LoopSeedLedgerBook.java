package com.moakiee.ae2lt.crafting.timewheel;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.moakiee.thunderbolt.core.crafting.support.CraftingPatternDelegates;
import com.moakiee.ae2lt.crafting.runtime.ExecuteLoopPattern;
import com.moakiee.ae2lt.overload.runtime.pattern.OverloadedProviderOnlyPatternDetails;
import com.moakiee.thunderbolt.core.crafting.planner.Sat;
import java.math.BigInteger;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.BiPredicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * Signed reusable-seed accounts owned by loop consumers.
 *
 * <p>Items remain in the CPU's one physical inventory. A positive account balance is a logical
 * claim that protects an item for that consumer; a negative balance records that the consumer ran
 * ahead using otherwise-free CPU stock. Internal outputs are credited directly to their fixed
 * consumer accounts, so two members that consume the same key cannot steal one another's share.</p>
 */
final class LoopSeedLedgerBook {
    private static final String TAG_LEDGERS = "loopSeedLedger";
    private static final String TAG_CONSUMER_ID = "consumerId";
    private static final String TAG_GROUP_IDS = "groupIds";
    private static final String TAG_GROUP_ID = "groupId";
    private static final String TAG_ENTRIES = "entries";
    private static final String TAG_NEGATIVE = "negative";
    private static final String TAG_DEBTS = "variantDebts";
    private static final String TAG_PLANNED = "planned";
    private static final String TAG_VARIANT_RULES = "variantRules";
    private static final String TAG_RULE_EXACT = "exact";
    private static final String TAG_RULE_FUZZY = "fuzzyIdentities";
    private static final String TAG_BUNDLE_UNITS = "bundleUnits";
    private static final String TAG_SINGLE_SEED_CONSUMER = "singleSeedConsumer";

    private final Map<UUID, Map<AEKey, Long>> ledgers = new HashMap<>();
    private final Map<UUID, Set<UUID>> consumerGroups = new HashMap<>();
    /** Persisted planned-key acceptance rules used after the last consumer task was dispatched. */
    private final Map<UUID, Map<AEKey, ExecuteLoopPattern.SeedVariantRule>> variantRules =
            new HashMap<>();
    /** Physical units of one complete P2 firing for each logical seed key. */
    private final Map<UUID, Map<AEKey, Long>> consumerBundleUnits = new HashMap<>();
    private final Set<UUID> singleSeedConsumers = new LinkedHashSet<>();
    /** Bootstrap capacities retained for assigning concrete host variants without greedy theft. */
    private final Map<UUID, Map<AEKey, Long>> hostBootstrapRequirements = new HashMap<>();
    /** consumer -> planned logical key -> actual borrowed key -> positive debt amount. */
    private final Map<UUID, Map<AEKey, Map<AEKey, Long>>> variantDebts = new HashMap<>();
    private final Map<AEKey, BigInteger> totalReserved = new HashMap<>();

    void initialize(Iterable<ExecuteLoopPattern> patterns) {
        clear();
        var dedicatedInitial = new LinkedHashMap<UUID, Map<AEKey, Long>>();
        var sharedInitialByGroup =
                new LinkedHashMap<UUID, Map<UUID, Map<AEKey, Long>>>();
        if (patterns != null) {
            for (var pattern : patterns) {
                if (pattern == null) continue;
                registerConsumer(pattern);
                var account = pattern.hasSingleSeedInputPerMember()
                        ? sharedInitialByGroup
                                .computeIfAbsent(pattern.reusableSeedGroupId(),
                                        ignored -> new LinkedHashMap<>())
                                .computeIfAbsent(pattern.seedConsumerId(),
                                        ignored -> new LinkedHashMap<>())
                        : dedicatedInitial.computeIfAbsent(
                                pattern.seedConsumerId(), ignored -> new LinkedHashMap<>());
                for (var seed : pattern.initialSeed()) {
                    if (seed.getLongValue() > 0) {
                        account.merge(seed.getKey(), seed.getLongValue(), Math::max);
                        hostBootstrapRequirements
                                .computeIfAbsent(pattern.seedConsumerId(),
                                        ignored -> new LinkedHashMap<>())
                                .merge(seed.getKey(), seed.getLongValue(), Math::max);
                    }
                }
            }
        }
        for (var account : dedicatedInitial.entrySet()) {
            for (var seed : account.getValue().entrySet()) {
                adjust(account.getKey(), seed.getKey(), seed.getValue());
            }
        }
        var sharedMaximum = new LinkedHashMap<AEKey, Long>();
        for (var group : sharedInitialByGroup.values()) {
            var groupTotal = new LinkedHashMap<AEKey, Long>();
            for (var consumer : group.values()) {
                for (var seed : consumer.entrySet()) {
                    groupTotal.merge(seed.getKey(), seed.getValue(), Sat::add);
                }
            }
            for (var seed : groupTotal.entrySet()) {
                sharedMaximum.merge(seed.getKey(), seed.getValue(), Math::max);
            }
        }
        for (var seed : sharedMaximum.entrySet()) {
            adjust(ExecuteLoopPattern.SHARED_SEED_ACCOUNT_ID, seed.getKey(), seed.getValue());
        }
    }

    /** Package-private deterministic initializer used by focused accounting tests. */
    void initializeAccounts(Map<UUID, Map<AEKey, Long>> accounts) {
        clear();
        if (accounts == null) return;
        for (var account : accounts.entrySet()) {
            if (account.getKey() == null || account.getValue() == null) continue;
            for (var seed : account.getValue().entrySet()) {
                if (seed.getKey() != null && seed.getValue() != null && seed.getValue() > 0) {
                    adjust(account.getKey(), seed.getKey(), seed.getValue());
                }
            }
        }
    }

    void registerConsumers(Iterable<ExecuteLoopPattern> patterns) {
        if (patterns == null) return;
        for (var pattern : patterns) {
            if (pattern != null) registerConsumer(pattern);
        }
    }

    private void registerConsumer(ExecuteLoopPattern pattern) {
        consumerGroups.computeIfAbsent(pattern.seedConsumerId(), ignored -> new LinkedHashSet<>())
                .add(pattern.reusableSeedGroupId());
        if (pattern.hasSingleSeedInputPerMember()) {
            singleSeedConsumers.add(pattern.seedConsumerId());
        }
        var rules = variantRules.computeIfAbsent(
                pattern.seedConsumerId(), ignored -> new LinkedHashMap<>());
        for (var input : pattern.inputSeed()) {
            var rule = pattern.seedVariantRule(input.getKey());
            rules.merge(input.getKey(), rule, ExecuteLoopPattern.SeedVariantRule::merge);
            registerBundleUnits(
                    pattern.seedConsumerId(), input.getKey(), input.getLongValue());
        }
        for (var target : pattern.outputSeedCredits().keySet()) {
            consumerGroups.computeIfAbsent(target, ignored -> new LinkedHashSet<>())
                    .add(pattern.reusableSeedGroupId());
        }
        for (var target : pattern.sharedOutputSeedCredits().keySet()) {
            consumerGroups.computeIfAbsent(target, ignored -> new LinkedHashSet<>())
                    .add(pattern.reusableSeedGroupId());
        }
    }

    /** Aggregate reserve minus the positive claim owned by the requesting consumer. */
    Map<AEKey, Long> reservationView(
            @Nullable UUID ownConsumer,
            Predicate<AEKey> allowedOwnSeedInput) {
        return reservationView(ownConsumer, allowedOwnSeedInput, false);
    }

    Map<AEKey, Long> reservationView(
            @Nullable UUID ownConsumer,
            Predicate<AEKey> allowedOwnSeedInput,
            boolean mayUseSharedSeed) {
        Predicate<AEKey> allowed = allowedOwnSeedInput != null
                ? allowedOwnSeedInput : ignored -> false;
        return new AbstractMap<>() {
            @Override
            public Long get(Object key) {
                if (!(key instanceof AEKey aeKey)) return null;
                long reserved = totalReserved(aeKey);
                if (reserved <= 0) return null;
                if (ownConsumer != null && allowed.test(aeKey)) {
                    reserved = Math.max(0L, reserved
                            - Math.max(0L, balance(ownConsumer, aeKey)));
                    if (mayUseSharedSeed) {
                        reserved = Math.max(0L, reserved - Math.max(0L, balance(
                                ExecuteLoopPattern.SHARED_SEED_ACCOUNT_ID, aeKey)));
                    }
                }
                return reserved > 0 ? reserved : null;
            }

            @Override
            public boolean containsKey(Object key) {
                return get(key) != null;
            }

            @Override
            public boolean isEmpty() {
                if (totalReserved.isEmpty()) return true;
                for (var key : totalReserved.keySet()) {
                    if (get(key) != null) return false;
                }
                return true;
            }

            @Override
            public Set<Entry<AEKey, Long>> entrySet() {
                var entries = new LinkedHashSet<Entry<AEKey, Long>>();
                for (var key : totalReserved.keySet()) {
                    var value = get(key);
                    if (value != null) entries.add(Map.entry(key, value));
                }
                return Set.copyOf(entries);
            }
        };
    }

    boolean hasReservations() {
        return !totalReserved.isEmpty();
    }

    /** Validates the concrete substitution transition before inputs are handed to a provider. */
    boolean canRouteActualSeedUses(
            ExecuteLoopPattern pattern,
            @Nullable List<ExecuteLoopPattern.ActualSeedUse> actualInputUses) {
        if (pattern == null || actualInputUses == null) return true;
        var mappedInputs = new KeyCounter();
        for (var use : actualInputUses) mappedInputs.add(use.planned(), use.amount());
        var plannedSeed = pattern.inputSeed();
        for (var planned : plannedSeed) {
            if (mappedInputs.get(planned.getKey()) != planned.getLongValue()) return false;
        }
        for (var mapped : mappedInputs) {
            if (mapped.getLongValue() != plannedSeed.get(mapped.getKey())) return false;
        }

        return planRemainderRoutes(pattern, 1L, actualInputUses) != null;
    }

    Map<UUID, KeyCounter> recordDispatch(
            ExecuteLoopPattern pattern,
            long copies,
            boolean sharedBatch,
            @Nullable List<ExecuteLoopPattern.ActualSeedUse> actualInputUses) {
        if (pattern == null || copies <= 0) return Map.of();
        registerConsumer(pattern);
        long scale = sharedBatch ? 1L : copies;
        if (actualInputUses != null) {
            for (var use : actualInputUses) {
                debit(pattern, use.planned(), use.actual(), use.amount());
            }
        } else {
            for (var input : pattern.inputSeed()) {
                long amount = Sat.mul(input.getLongValue(), scale);
                if (amount > 0) debit(pattern, input.getKey(), input.getKey(), amount);
            }
        }
        var routing = planRemainderRoutes(pattern, scale, actualInputUses);
        if (routing == null) {
            throw new IllegalStateException(
                    "actual reusable remainder cannot reach its fixed consumer");
        }
        var remainderCredits = allocatePhysicalRemainderCredits(
                routing, actualInputUses, true);
        for (var quota : routing.quotas) {
            if (quota.remaining > 0) routeCredit(quota, quota.planned, quota.remaining);
        }
        return remainderCredits;
    }

    /** Previews the exact P2 shares already supplied by concrete input remainders. */
    Map<UUID, KeyCounter> previewRemainderCredits(
            ExecuteLoopPattern pattern,
            long copies,
            boolean sharedBatch,
            @Nullable List<ExecuteLoopPattern.ActualSeedUse> actualInputUses) {
        if (pattern == null || copies <= 0) return Map.of();
        registerConsumer(pattern);
        long scale = sharedBatch ? 1L : copies;
        var routing = planRemainderRoutes(pattern, scale, actualInputUses);
        if (routing == null) {
            throw new IllegalStateException(
                    "actual reusable remainder cannot reach its fixed consumer");
        }
        return allocatePhysicalRemainderCredits(routing, actualInputUses, false);
    }

    private Map<UUID, KeyCounter> allocatePhysicalRemainderCredits(
            RemainderRouting routing,
            @Nullable List<ExecuteLoopPattern.ActualSeedUse> actualInputUses,
            boolean apply) {
        // Credits physically supplied by input remainders must not be assigned a second time to
        // an overloaded output slot. Return both changed and unchanged remainder allocations to
        // the caller; deterministic explicit outputs are deliberately left for output-slot
        // accounting.
        var remainderCredits = new LinkedHashMap<UUID, KeyCounter>();
        for (var allocation : routing.allocations) {
            if (apply) routeCredit(allocation.quota, allocation.actual, allocation.amount);
            allocation.quota.remaining -= allocation.amount;
            recordPhysicalRemainderCredit(
                    remainderCredits, allocation.quota, allocation.amount);
        }
        var unchangedRemainders = deterministicUnchangedRemainderCapacity(actualInputUses);
        for (var quota : routing.quotas) {
            if (quota.remaining <= 0) continue;
            long available = unchangedRemainders.getOrDefault(quota.planned, 0L);
            long amount = Math.min(quota.remaining, available);
            if (amount <= 0) continue;
            if (apply) routeCredit(quota, quota.planned, amount);
            quota.remaining -= amount;
            if (amount == available) unchangedRemainders.remove(quota.planned);
            else unchangedRemainders.put(quota.planned, available - amount);
            recordPhysicalRemainderCredit(remainderCredits, quota, amount);
        }
        return Map.copyOf(remainderCredits);
    }

    private static void recordPhysicalRemainderCredit(
            Map<UUID, KeyCounter> result, CreditQuota quota, long amount) {
        if (amount <= 0) return;
        var runtimeConsumer = quota.shared
                ? ExecuteLoopPattern.SHARED_SEED_ACCOUNT_ID
                : quota.beneficiary;
        result.computeIfAbsent(runtimeConsumer, ignored -> new KeyCounter())
                .add(quota.planned, amount);
    }

    /** Package-private exact-key transition used by focused accounting tests. */
    void recordDispatch(
            UUID consumer,
            KeyCounter inputSeed,
            Map<UUID, KeyCounter> outputCredits,
            long scale) {
        if (consumer == null || scale <= 0) return;
        if (inputSeed != null) {
            for (var input : inputSeed) {
                long amount = Sat.mul(input.getLongValue(), scale);
                if (amount > 0) adjust(consumer, input.getKey(), -amount);
            }
        }
        if (outputCredits != null) {
            for (var target : outputCredits.entrySet()) {
                if (target.getKey() == null || target.getValue() == null) continue;
                for (var output : target.getValue()) {
                    long amount = Sat.mul(output.getLongValue(), scale);
                    if (amount > 0) {
                        creditConsumer(target.getKey(), output.getKey(), output.getKey(), amount);
                    }
                }
            }
        }
    }

    private void debit(ExecuteLoopPattern pattern, AEKey planned, AEKey actual, long amount) {
        if (pattern == null || planned == null || actual == null || amount <= 0) return;
        var consumer = pattern.seedConsumerId();
        long remaining = amount;
        long owned = Math.min(remaining, Math.max(0L, balance(consumer, actual)));
        if (owned > 0) {
            adjust(consumer, actual, -owned);
            remaining -= owned;
        }
        if (remaining > 0 && pattern.hasSingleSeedInputPerMember()) {
            long shared = Math.min(remaining, Math.max(0L, balance(
                    ExecuteLoopPattern.SHARED_SEED_ACCOUNT_ID, actual)));
            if (shared > 0) {
                adjust(ExecuteLoopPattern.SHARED_SEED_ACCOUNT_ID, actual, -shared);
                remaining -= shared;
            }
        }
        if (remaining > 0) {
            adjust(consumer, actual, -remaining);
            addVariantDebt(consumer, planned, actual, remaining);
        }
    }

    /** Rekeys only positive credit still waiting in the specified consumer account. */
    void rekeyAvailable(UUID consumer, AEKey expected, AEKey actual, long amount) {
        if (consumer == null || expected == null || actual == null
                || expected.equals(actual) || amount <= 0) return;
        long moved = Math.min(amount, Math.max(0L, balance(consumer, expected)));
        if (moved <= 0) return;
        adjust(consumer, expected, -moved);
        adjust(consumer, actual, moved);
    }

    /**
     * Assigns a whole host-return batch with residual matching. The returned counter contains only
     * variants that could be bound without stealing a constrained consumer's bootstrap state.
     */
    KeyCounter assignHostVariantsForGroup(
            UUID group, boolean sharedPool, AEKey expected, KeyCounter offered) {
        var accepted = new KeyCounter();
        if (group == null || expected == null || offered == null || offered.isEmpty()) {
            return accepted;
        }
        var variants = new ArrayList<GenericStack>();
        for (var entry : offered) {
            if (entry.getLongValue() > 0) {
                variants.add(new GenericStack(entry.getKey(), entry.getLongValue()));
            }
        }
        if (variants.isEmpty()) return accepted;

        var consumers = new ArrayList<UUID>();
        for (var consumer : consumerGroups.keySet()) {
            if (!consumerGroups.getOrDefault(consumer, Set.of()).contains(group)) continue;
            if (sharedPool && !singleSeedConsumers.contains(consumer)) continue;
            long capacity = hostBootstrapRequirements
                    .getOrDefault(consumer, Map.of()).getOrDefault(expected, 0L);
            if (capacity > 0) consumers.add(consumer);
        }
        consumers.sort(UUID::compareTo);
        if (consumers.isEmpty()) return accepted;

        var supply = new long[variants.size()];
        for (int i = 0; i < variants.size(); i++) supply[i] = variants.get(i).amount();
        var capacity = new long[consumers.size()];
        for (int i = 0; i < consumers.size(); i++) {
            capacity[i] = hostBootstrapRequirements
                    .getOrDefault(consumers.get(i), Map.of()).getOrDefault(expected, 0L);
        }
        var match = matchCapacitiesPartially(supply, capacity, (variantIndex, consumerIndex) -> {
            var actual = variants.get(variantIndex).what();
            return acceptsVariant(consumers.get(consumerIndex), expected, actual);
        });
        if (match == null) return accepted;
        if (!hostAssignmentsUseWholeBundles(expected, variants, consumers, match.flows)) {
            // Keep usable exact planned stock even when the offered concrete variants form only
            // partial P2 bundles. The rejected variants return to the host and the exact shortfall
            // remains available for ordinary seed planning.
            match = matchCapacitiesPartially(supply, capacity,
                    (variantIndex, consumerIndex) -> expected.equals(
                            variants.get(variantIndex).what()));
            if (match == null) return accepted;
        }

        for (int variantIndex = 0; variantIndex < variants.size(); variantIndex++) {
            long acceptedVariant = 0L;
            for (int consumerIndex = 0; consumerIndex < consumers.size(); consumerIndex++) {
                long amount = match.flows[variantIndex][consumerIndex];
                if (amount <= 0) continue;
                var consumer = consumers.get(consumerIndex);
                if (sharedPool) {
                    if (!expected.equals(variants.get(variantIndex).what())) {
                        adjust(ExecuteLoopPattern.SHARED_SEED_ACCOUNT_ID, expected, -amount);
                        adjust(consumer, variants.get(variantIndex).what(), amount);
                    }
                } else {
                    adjust(consumer, expected, -amount);
                    adjust(consumer, variants.get(variantIndex).what(), amount);
                }
                var bootstrap = hostBootstrapRequirements.get(consumer);
                long left = bootstrap.getOrDefault(expected, 0L) - amount;
                if (left > 0) bootstrap.put(expected, left);
                else bootstrap.remove(expected);
                acceptedVariant = Sat.add(acceptedVariant, amount);
            }
            if (acceptedVariant > 0) {
                accepted.add(variants.get(variantIndex).what(), acceptedVariant);
            }
        }
        return accepted;
    }

    void clear() {
        ledgers.clear();
        consumerGroups.clear();
        variantRules.clear();
        consumerBundleUnits.clear();
        singleSeedConsumers.clear();
        hostBootstrapRequirements.clear();
        variantDebts.clear();
        totalReserved.clear();
    }

    void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        clear();
        var accountTags = data.getList(TAG_LEDGERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < accountTags.size(); i++) {
            var accountTag = accountTags.getCompound(i);
            if (!accountTag.hasUUID(TAG_CONSUMER_ID)) continue;
            var consumer = accountTag.getUUID(TAG_CONSUMER_ID);
            if (accountTag.getBoolean(TAG_SINGLE_SEED_CONSUMER)) {
                singleSeedConsumers.add(consumer);
            }
            var groups = accountTag.getList(TAG_GROUP_IDS, Tag.TAG_COMPOUND);
            for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
                var groupTag = groups.getCompound(groupIndex);
                if (groupTag.hasUUID(TAG_GROUP_ID)) {
                    consumerGroups.computeIfAbsent(consumer, ignored -> new LinkedHashSet<>())
                            .add(groupTag.getUUID(TAG_GROUP_ID));
                }
            }
            var entries = accountTag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
            for (int entryIndex = 0; entryIndex < entries.size(); entryIndex++) {
                var entryTag = entries.getCompound(entryIndex);
                var stack = GenericStack.readTag(entryTag);
                if (stack == null || stack.amount() <= 0) continue;
                adjust(consumer, stack.what(), entryTag.getBoolean(TAG_NEGATIVE)
                        ? -stack.amount() : stack.amount());
            }
            var debts = accountTag.getList(TAG_DEBTS, Tag.TAG_COMPOUND);
            for (int debtIndex = 0; debtIndex < debts.size(); debtIndex++) {
                var debtTag = debts.getCompound(debtIndex);
                var actual = GenericStack.readTag(debtTag);
                var planned = GenericStack.readTag(
                        debtTag.getCompound(TAG_PLANNED));
                if (actual == null || planned == null || actual.amount() <= 0) continue;
                addVariantDebt(consumer, planned.what(), actual.what(), actual.amount());
            }
            var rules = accountTag.getList(TAG_VARIANT_RULES, Tag.TAG_COMPOUND);
            for (int ruleIndex = 0; ruleIndex < rules.size(); ruleIndex++) {
                var ruleTag = rules.getCompound(ruleIndex);
                var planned = GenericStack.readTag(
                        ruleTag.getCompound(TAG_PLANNED));
                if (planned == null) continue;
                var exact = readRuleKeys(ruleTag, TAG_RULE_EXACT, registries);
                var fuzzy = readRuleKeys(ruleTag, TAG_RULE_FUZZY, registries);
                variantRules
                        .computeIfAbsent(consumer, ignored -> new LinkedHashMap<>())
                        .merge(planned.what(),
                                new ExecuteLoopPattern.SeedVariantRule(exact, fuzzy),
                                ExecuteLoopPattern.SeedVariantRule::merge);
                if (ruleTag.contains(TAG_BUNDLE_UNITS, Tag.TAG_LONG)) {
                    registerBundleUnits(
                            consumer, planned.what(), ruleTag.getLong(TAG_BUNDLE_UNITS));
                }
            }
        }
    }

    void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        if (ledgers.isEmpty() && variantDebts.isEmpty() && variantRules.isEmpty()
                && consumerBundleUnits.isEmpty()
                && consumerGroups.isEmpty() && singleSeedConsumers.isEmpty()) {
            data.remove(TAG_LEDGERS);
            return;
        }
        var accountTags = new ListTag();
        var consumerIds = new LinkedHashSet<UUID>(ledgers.keySet());
        consumerIds.addAll(variantDebts.keySet());
        consumerIds.addAll(variantRules.keySet());
        consumerIds.addAll(consumerBundleUnits.keySet());
        consumerIds.addAll(consumerGroups.keySet());
        consumerIds.addAll(singleSeedConsumers);
        var consumers = new ArrayList<>(consumerIds);
        consumers.sort(UUID::compareTo);
        for (var consumer : consumers) {
            var entries = ledgers.getOrDefault(consumer, Map.of());
            var accountTag = new CompoundTag();
            accountTag.putUUID(TAG_CONSUMER_ID, consumer);
            if (singleSeedConsumers.contains(consumer)) {
                accountTag.putBoolean(TAG_SINGLE_SEED_CONSUMER, true);
            }
            var groupTags = new ListTag();
            var groups = new ArrayList<>(consumerGroups.getOrDefault(consumer, Set.of()));
            groups.sort(UUID::compareTo);
            for (var group : groups) {
                var groupTag = new CompoundTag();
                groupTag.putUUID(TAG_GROUP_ID, group);
                groupTags.add(groupTag);
            }
            if (!groupTags.isEmpty()) accountTag.put(TAG_GROUP_IDS, groupTags);
            var entryTags = new ListTag();
            for (var entry : entries.entrySet()) {
                if (entry.getValue() == 0) continue;
                long magnitude = entry.getValue() == Long.MIN_VALUE
                        ? Long.MAX_VALUE : Math.abs(entry.getValue());
                var entryTag = GenericStack.writeTag(
                        new GenericStack(entry.getKey(), magnitude));
                if (entry.getValue() < 0) entryTag.putBoolean(TAG_NEGATIVE, true);
                entryTags.add(entryTag);
            }
            if (!entryTags.isEmpty()) accountTag.put(TAG_ENTRIES, entryTags);
            var debtTags = new ListTag();
            var byPlanned = variantDebts.getOrDefault(consumer, Map.of());
            for (var planned : byPlanned.entrySet()) {
                for (var actual : planned.getValue().entrySet()) {
                    if (actual.getValue() <= 0) continue;
                    var debtTag = GenericStack.writeTag(
                            new GenericStack(actual.getKey(), actual.getValue()));
                    debtTag.put(TAG_PLANNED, GenericStack.writeTag(
                            new GenericStack(planned.getKey(), 1L)));
                    debtTags.add(debtTag);
                }
            }
            if (!debtTags.isEmpty()) accountTag.put(TAG_DEBTS, debtTags);
            var ruleTags = new ListTag();
            var plannedRuleKeys = new LinkedHashSet<AEKey>();
            plannedRuleKeys.addAll(variantRules.getOrDefault(consumer, Map.of()).keySet());
            plannedRuleKeys.addAll(consumerBundleUnits.getOrDefault(consumer, Map.of()).keySet());
            for (var planned : plannedRuleKeys) {
                var rule = variantRules.getOrDefault(consumer, Map.of()).getOrDefault(
                        planned, new ExecuteLoopPattern.SeedVariantRule(
                                Set.of(planned), Set.of()));
                var ruleTag = new CompoundTag();
                ruleTag.put(TAG_PLANNED, GenericStack.writeTag(
                        new GenericStack(planned, 1L)));
                writeRuleKeys(ruleTag, TAG_RULE_EXACT, rule.exactVariants(), registries);
                writeRuleKeys(
                        ruleTag, TAG_RULE_FUZZY, rule.fuzzyIdentities(), registries);
                long bundleUnits = consumerBundleUnits
                        .getOrDefault(consumer, Map.of()).getOrDefault(planned, 0L);
                if (consumerBundleUnits.getOrDefault(consumer, Map.of()).containsKey(planned)) {
                    ruleTag.putLong(TAG_BUNDLE_UNITS, bundleUnits);
                }
                ruleTags.add(ruleTag);
            }
            if (!ruleTags.isEmpty()) accountTag.put(TAG_VARIANT_RULES, ruleTags);
            if (entryTags.isEmpty() && debtTags.isEmpty() && ruleTags.isEmpty()
                    && groupTags.isEmpty() && !singleSeedConsumers.contains(consumer)) continue;
            accountTags.add(accountTag);
        }
        if (accountTags.isEmpty()) data.remove(TAG_LEDGERS);
        else data.put(TAG_LEDGERS, accountTags);
    }

    long balance(UUID consumer, AEKey key) {
        var ledger = ledgers.get(consumer);
        return ledger != null ? ledger.getOrDefault(key, 0L) : 0L;
    }

    long totalReserved(AEKey key) {
        var value = totalReserved.get(key);
        if (value == null || value.signum() <= 0) return 0L;
        return value.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) >= 0
                ? Long.MAX_VALUE : value.longValue();
    }

    Map<AEKey, Long> positiveSnapshot() {
        var result = new HashMap<AEKey, Long>();
        for (var key : totalReserved.keySet()) {
            long amount = totalReserved(key);
            if (amount > 0) result.put(key, amount);
        }
        return Map.copyOf(result);
    }

    private void adjust(UUID consumer, AEKey key, long delta) {
        if (consumer == null || key == null || delta == 0) return;
        var ledger = ledgers.computeIfAbsent(consumer, ignored -> new LinkedHashMap<>());
        long oldValue = ledger.getOrDefault(key, 0L);
        long newValue = saturatingSignedAdd(oldValue, delta);
        if (newValue == 0) ledger.remove(key);
        else ledger.put(key, newValue);
        if (ledger.isEmpty()) ledgers.remove(consumer);

        long oldPositive = Math.max(0L, oldValue);
        long newPositive = Math.max(0L, newValue);
        var exact = totalReserved.getOrDefault(key, BigInteger.ZERO);
        if (newPositive > oldPositive) {
            exact = exact.add(BigInteger.valueOf(newPositive - oldPositive));
        } else if (oldPositive > newPositive) {
            exact = exact.subtract(BigInteger.valueOf(oldPositive - newPositive));
        }
        if (exact.signum() <= 0) totalReserved.remove(key);
        else totalReserved.put(key, exact);
    }

    private List<CreditQuota> creditQuotas(ExecuteLoopPattern pattern, long scale) {
        var result = new ArrayList<CreditQuota>();
        addCreditQuotas(result, pattern.outputSeedCredits(), scale, false);
        addCreditQuotas(result, pattern.sharedOutputSeedCredits(), scale, true);
        return result;
    }

    private static void addCreditQuotas(
            List<CreditQuota> result,
            Map<UUID, KeyCounter> credits,
            long scale,
            boolean shared) {
        for (var target : credits.entrySet()) {
            for (var output : target.getValue()) {
                long amount = Sat.mul(output.getLongValue(), scale);
                if (amount > 0) {
                    result.add(new CreditQuota(
                            target.getKey(), output.getKey(), amount, shared));
                }
            }
        }
    }

    private void routeCredit(CreditQuota quota, AEKey physical, long amount) {
        if (quota.shared) {
            creditShared(quota.beneficiary, quota.planned, physical, amount);
        } else {
            creditConsumer(quota.beneficiary, quota.planned, physical, amount);
        }
    }

    /** Applies a fixed producer credit to one consumer, paying its exact logical debt first. */
    private void creditConsumer(UUID consumer, AEKey planned, AEKey physical, long amount) {
        if (consumer == null || planned == null || physical == null || amount <= 0) return;
        long remaining = repayVariantDebt(consumer, planned, amount);
        if (remaining > 0) adjust(consumer, physical, remaining);
    }

    /** A safe full-cycle credit first repays its beneficiary, then returns to the shared pool. */
    private void creditShared(UUID beneficiary, AEKey planned, AEKey physical, long amount) {
        if (beneficiary == null || planned == null || physical == null || amount <= 0) return;
        long remaining = repayVariantDebt(beneficiary, planned, amount);
        if (remaining > 0) {
            if (planned.equals(physical)) {
                adjust(ExecuteLoopPattern.SHARED_SEED_ACCOUNT_ID, physical, remaining);
            } else {
                // A component-changing state is not globally fungible. Keep it attached to the
                // fixed beneficiary even if a third-party task incorrectly advertised sharing.
                adjust(beneficiary, physical, remaining);
            }
        }
    }

    private void addVariantDebt(
            UUID consumer, AEKey planned, AEKey actual, long amount) {
        if (consumer == null || planned == null || actual == null || amount <= 0) return;
        variantDebts
                .computeIfAbsent(consumer, ignored -> new LinkedHashMap<>())
                .computeIfAbsent(planned, ignored -> new LinkedHashMap<>())
                .merge(actual, amount, Sat::add);
    }

    /** Returns the part of {@code amount} not consumed by this logical planned debt. */
    private long repayVariantDebt(UUID consumer, AEKey planned, long amount) {
        if (consumer == null || planned == null || amount <= 0) return Math.max(0L, amount);
        var byPlanned = variantDebts.get(consumer);
        if (byPlanned == null) return amount;
        var byActual = byPlanned.get(planned);
        if (byActual == null) return amount;
        long remaining = amount;
        var iterator = byActual.entrySet().iterator();
        while (iterator.hasNext() && remaining > 0) {
            var debt = iterator.next();
            long repaid = Math.min(remaining, debt.getValue());
            if (repaid <= 0) continue;
            adjust(consumer, debt.getKey(), repaid);
            remaining -= repaid;
            long left = debt.getValue() - repaid;
            if (left <= 0) iterator.remove();
            else debt.setValue(left);
        }
        if (byActual.isEmpty()) byPlanned.remove(planned);
        if (byPlanned.isEmpty()) variantDebts.remove(consumer);
        return remaining;
    }

    private boolean acceptsVariant(UUID consumer, AEKey planned, AEKey actual) {
        if (planned == null || actual == null) return false;
        if (planned.equals(actual)) return true;
        if (variantDebt(consumer, planned, actual) > 0) return true;
        var persisted = variantRules.getOrDefault(consumer, Map.of()).get(planned);
        if (persisted != null && persisted.accepts(actual)) return true;
        return false;
    }

    /** Whether a returned fuzzy/overload variant may be assigned to this runtime account. */
    boolean acceptsReturnedVariant(UUID account, AEKey planned, AEKey actual) {
        if (account == null || planned == null || actual == null) return false;
        if (account.equals(ExecuteLoopPattern.SHARED_SEED_ACCOUNT_ID)) {
            return planned.equals(actual);
        }
        return balance(account, planned) <= 0L || acceptsVariant(account, planned, actual);
    }

    /** Late-bound ID_ONLY outputs can be safely re-keyed unit-by-unit only for one-unit bundles. */
    boolean acceptsLateBoundVariantCredit(UUID consumer, AEKey planned) {
        return bundleUnits(consumer, planned) == 1L;
    }

    private static Set<AEKey> readRuleKeys(
            CompoundTag owner,
            String name,
            HolderLookup.Provider registries) {
        var result = new LinkedHashSet<AEKey>();
        var tags = owner.getList(name, Tag.TAG_COMPOUND);
        for (int i = 0; i < tags.size(); i++) {
            var stack = GenericStack.readTag(tags.getCompound(i));
            if (stack != null) result.add(stack.what());
        }
        return Set.copyOf(result);
    }

    private static void writeRuleKeys(
            CompoundTag owner,
            String name,
            Set<AEKey> keys,
            HolderLookup.Provider registries) {
        if (keys.isEmpty()) return;
        var tags = new ListTag();
        for (var key : keys) {
            tags.add(GenericStack.writeTag(new GenericStack(key, 1L)));
        }
        owner.put(name, tags);
    }

    private long variantDebt(UUID consumer, AEKey planned, AEKey actual) {
        var byPlanned = variantDebts.get(consumer);
        if (byPlanned == null) return 0L;
        var byActual = byPlanned.get(planned);
        return byActual != null ? byActual.getOrDefault(actual, 0L) : 0L;
    }

    private void registerBundleUnits(UUID consumer, AEKey planned, long units) {
        if (consumer == null || planned == null || units < 0) return;
        var byPlanned = consumerBundleUnits.computeIfAbsent(
                consumer, ignored -> new LinkedHashMap<>());
        var previous = byPlanned.get(planned);
        if (previous == null) byPlanned.put(planned, units);
        else if (previous != units) byPlanned.put(planned, 0L);
    }

    private long bundleUnits(UUID consumer, AEKey planned) {
        return consumerBundleUnits
                .getOrDefault(consumer, Map.of()).getOrDefault(planned, 0L);
    }

    private @Nullable RemainderRouting planRemainderRoutes(
            ExecuteLoopPattern pattern,
            long scale,
            @Nullable List<ExecuteLoopPattern.ActualSeedUse> actualInputUses) {
        var quotas = creditQuotas(pattern, scale);
        var quotaByPlanned = new LinkedHashMap<AEKey, Long>();
        for (var quota : quotas) {
            quotaByPlanned.merge(quota.planned, quota.remaining, Sat::add);
        }
        var exactOutputByPlanned = deterministicExactOutputCapacity(
                pattern, scale, actualInputUses);
        var variants = new ArrayList<RemainderDemand>();
        if (actualInputUses != null) {
            for (var use : actualInputUses) {
                if (use.plannedRemainder() == null
                        || quotaByPlanned.getOrDefault(use.plannedRemainder(), 0L) <= 0) continue;
                if (use.actualRemainder() == null) return null;
                if (use.plannedRemainder().equals(use.actualRemainder())) continue;
                if (use.remainderAmount() <= 0) return null;
                variants.add(new RemainderDemand(
                        use.plannedRemainder(), use.actualRemainder(), use.remainderAmount()));
            }
        }
        if (variants.isEmpty()) return new RemainderRouting(quotas, List.of());

        var supply = new long[variants.size()];
        for (int i = 0; i < variants.size(); i++) supply[i] = variants.get(i).amount;
        var capacity = new long[quotas.size()];
        for (int i = 0; i < quotas.size(); i++) capacity[i] = quotas.get(i).remaining;
        var match = matchCapacitiesPartially(supply, capacity, (variantIndex, quotaIndex) -> {
            var variant = variants.get(variantIndex);
            var quota = quotas.get(quotaIndex);
            return quota.planned.equals(variant.planned)
                    && acceptsVariant(quota.beneficiary, quota.planned, variant.actual);
        });
        if (match == null) return null;
        // Strict explicit outputs and unchanged remainders satisfy the planned-key quota first.
        // Only the still-uncovered seed prefix must take a changed concrete remainder. Any further
        // changed remainder is a public net output and must not make a valid transition fail.
        var requiredChangedByPlanned = new LinkedHashMap<AEKey, Long>();
        for (var planned : quotaByPlanned.keySet()) {
            long variantSupply = 0L;
            long matched = 0L;
            for (int variantIndex = 0; variantIndex < variants.size(); variantIndex++) {
                if (!planned.equals(variants.get(variantIndex).planned)) continue;
                variantSupply = Sat.add(variantSupply, supply[variantIndex]);
                for (int quotaIndex = 0; quotaIndex < quotas.size(); quotaIndex++) {
                    if (planned.equals(quotas.get(quotaIndex).planned)) {
                        matched = Sat.add(matched, match.flows[variantIndex][quotaIndex]);
                    }
                }
            }
            long exactCapacity = exactOutputByPlanned.getOrDefault(planned, 0L);
            long uncoveredQuota = Math.max(0L, quotaByPlanned.get(planned) - exactCapacity);
            long required = Math.min(variantSupply, uncoveredQuota);
            if (matched < required) return null;
            if (required > 0) requiredChangedByPlanned.put(planned, required);
        }

        var allocations = new ArrayList<RemainderAllocation>();
        for (int variantIndex = 0; variantIndex < variants.size(); variantIndex++) {
            var planned = variants.get(variantIndex).planned;
            long required = requiredChangedByPlanned.getOrDefault(planned, 0L);
            if (required <= 0) continue;
            for (int quotaIndex = 0; quotaIndex < quotas.size(); quotaIndex++) {
                long amount = Math.min(required, match.flows[variantIndex][quotaIndex]);
                if (amount > 0) {
                    allocations.add(new RemainderAllocation(
                            quotas.get(quotaIndex), variants.get(variantIndex).actual, amount));
                    required -= amount;
                    requiredChangedByPlanned.put(planned, required);
                }
            }
        }
        if (!usesWholeConsumerBundles(allocations)) return null;
        return new RemainderRouting(quotas, List.copyOf(allocations));
    }

    /**
     * A component-changing state must replace one complete P2 quota. Mixing one changed remainder
     * with an exact/unknown return can create a multiset that no single input slot can consume on
     * the next round (for example {@code 2X -> X + A}). Full slot/bundle routing belongs in a
     * future extractor rewrite; until then fail closed instead of admitting a latent deadlock.
     */
    private boolean usesWholeConsumerBundles(List<RemainderAllocation> allocations) {
        var amounts = new LinkedHashMap<ConcreteBundle, Long>();
        for (var allocation : allocations) {
            var consumer = allocation.quota.shared
                    ? ExecuteLoopPattern.SHARED_SEED_ACCOUNT_ID
                    : allocation.quota.beneficiary;
            amounts.merge(new ConcreteBundle(
                            consumer, allocation.quota.planned, allocation.actual),
                    allocation.amount, Sat::add);
        }
        for (var allocation : amounts.entrySet()) {
            long bundle = bundleUnits(
                    allocation.getKey().consumer, allocation.getKey().planned);
            if (bundle <= 0 || allocation.getValue() % bundle != 0) return false;
        }
        return true;
    }

    private boolean hostAssignmentsUseWholeBundles(
            AEKey planned,
            List<GenericStack> variants,
            List<UUID> consumers,
            long[][] flows) {
        var amounts = new LinkedHashMap<ConcreteBundle, Long>();
        for (int variantIndex = 0; variantIndex < variants.size(); variantIndex++) {
            var actual = variants.get(variantIndex).what();
            if (planned.equals(actual)) continue;
            for (int consumerIndex = 0; consumerIndex < consumers.size(); consumerIndex++) {
                long amount = flows[variantIndex][consumerIndex];
                if (amount <= 0) continue;
                amounts.merge(new ConcreteBundle(
                                consumers.get(consumerIndex), planned, actual),
                        amount, Sat::add);
            }
        }
        for (var allocation : amounts.entrySet()) {
            long bundle = bundleUnits(
                    allocation.getKey().consumer, allocation.getKey().planned);
            if (bundle <= 0 || allocation.getValue() % bundle != 0) return false;
        }
        return true;
    }

    /** Physical output capacity that is guaranteed to retain the planned key for this dispatch. */
    private static Map<AEKey, Long> deterministicExactOutputCapacity(
            ExecuteLoopPattern pattern,
            long scale,
            @Nullable List<ExecuteLoopPattern.ActualSeedUse> actualInputUses) {
        var result = new LinkedHashMap<AEKey, Long>();
        var providerPattern = CraftingPatternDelegates.forProviderLookup(pattern);
        var overload = providerPattern instanceof OverloadedProviderOnlyPatternDetails details
                ? details : null;
        var outputs = pattern.getOutputs();
        for (int slot = 0; slot < outputs.length; slot++) {
            var output = outputs[slot];
            if (output.what() == null || output.amount() <= 0
                    || (overload != null && overload.isFuzzyOutput(slot))) continue;
            result.merge(output.what(), Sat.mul(output.amount(), scale), Sat::add);
        }
        if (actualInputUses != null) {
            for (var use : actualInputUses) {
                if (use.plannedRemainder() == null
                        || !use.plannedRemainder().equals(use.actualRemainder())
                        || use.remainderAmount() <= 0) continue;
                result.merge(use.plannedRemainder(), use.remainderAmount(), Sat::add);
            }
        }
        return result;
    }

    /** Exact container/tool returns already known from this concrete dispatch. */
    private static Map<AEKey, Long> deterministicUnchangedRemainderCapacity(
            @Nullable List<ExecuteLoopPattern.ActualSeedUse> actualInputUses) {
        if (actualInputUses == null || actualInputUses.isEmpty()) return Map.of();
        var result = new LinkedHashMap<AEKey, Long>();
        for (var use : actualInputUses) {
            if (use.plannedRemainder() == null
                    || !use.plannedRemainder().equals(use.actualRemainder())
                    || use.remainderAmount() <= 0) continue;
            result.merge(use.plannedRemainder(), use.remainderAmount(), Sat::add);
        }
        return result;
    }

    private static @Nullable CapacityMatch matchCapacitiesPartially(
            long[] supply,
            long[] capacity,
            BiPredicate<Integer, Integer> allowed) {
        int supplyCount = supply.length;
        int capacityCount = capacity.length;
        int source = 0;
        int firstSupply = 1;
        int firstCapacity = firstSupply + supplyCount;
        int sink = firstCapacity + capacityCount;
        @SuppressWarnings("unchecked")
        var graph = (List<FlowEdge>[]) new List<?>[sink + 1];
        for (int i = 0; i < graph.length; i++) graph[i] = new ArrayList<>();
        var sourceEdges = new FlowEdge[supplyCount];
        var assignmentEdges = new FlowEdge[supplyCount][capacityCount];
        for (int i = 0; i < supplyCount; i++) {
            if (supply[i] < 0) return null;
            sourceEdges[i] = addFlowEdge(graph, source, firstSupply + i, supply[i]);
        }
        for (int j = 0; j < capacityCount; j++) {
            if (capacity[j] < 0) return null;
            addFlowEdge(graph, firstCapacity + j, sink, capacity[j]);
        }
        for (int i = 0; i < supplyCount; i++) {
            for (int j = 0; j < capacityCount; j++) {
                if (allowed.test(i, j)) {
                    assignmentEdges[i][j] = addFlowEdge(
                            graph, firstSupply + i, firstCapacity + j, Long.MAX_VALUE);
                }
            }
        }
        var level = new int[graph.length];
        while (buildFlowLevels(graph, source, sink, level)) {
            var next = new int[graph.length];
            while (pushFlow(graph, source, sink, Long.MAX_VALUE, level, next) > 0) {
                // Keep augmenting this level graph.
            }
        }
        boolean complete = true;
        for (var edge : sourceEdges) complete &= edge.capacity == 0L;
        var result = new long[supplyCount][capacityCount];
        for (int i = 0; i < supplyCount; i++) {
            for (int j = 0; j < capacityCount; j++) {
                var edge = assignmentEdges[i][j];
                if (edge != null) result[i][j] = edge.reverse.capacity;
            }
        }
        return new CapacityMatch(result, complete);
    }

    private static FlowEdge addFlowEdge(
            List<FlowEdge>[] graph, int from, int to, long capacity) {
        var forward = new FlowEdge(to, capacity);
        var reverse = new FlowEdge(from, 0L);
        forward.reverse = reverse;
        reverse.reverse = forward;
        graph[from].add(forward);
        graph[to].add(reverse);
        return forward;
    }

    private static boolean buildFlowLevels(
            List<FlowEdge>[] graph, int source, int sink, int[] level) {
        java.util.Arrays.fill(level, -1);
        var queue = new java.util.ArrayDeque<Integer>();
        level[source] = 0;
        queue.add(source);
        while (!queue.isEmpty()) {
            int node = queue.removeFirst();
            for (var edge : graph[node]) {
                if (edge.capacity <= 0 || level[edge.to] >= 0) continue;
                level[edge.to] = level[node] + 1;
                queue.addLast(edge.to);
            }
        }
        return level[sink] >= 0;
    }

    private static long pushFlow(
            List<FlowEdge>[] graph,
            int node,
            int sink,
            long offered,
            int[] level,
            int[] next) {
        if (node == sink) return offered;
        while (next[node] < graph[node].size()) {
            var edge = graph[node].get(next[node]);
            if (edge.capacity > 0 && level[edge.to] == level[node] + 1) {
                long sent = pushFlow(
                        graph, edge.to, sink, Math.min(offered, edge.capacity), level, next);
                if (sent > 0) {
                    edge.capacity -= sent;
                    edge.reverse.capacity += sent;
                    return sent;
                }
            }
            next[node]++;
        }
        return 0L;
    }

    private static final class FlowEdge {
        private final int to;
        private long capacity;
        private FlowEdge reverse;

        private FlowEdge(int to, long capacity) {
            this.to = to;
            this.capacity = capacity;
        }
    }

    private record RemainderDemand(AEKey planned, AEKey actual, long amount) { }
    private record ConcreteBundle(UUID consumer, AEKey planned, AEKey actual) { }
    private record RemainderAllocation(CreditQuota quota, AEKey actual, long amount) { }
    private record RemainderRouting(
            List<CreditQuota> quotas, List<RemainderAllocation> allocations) { }
    private record CapacityMatch(long[][] flows, boolean complete) { }

    private static final class CreditQuota {
        private final UUID beneficiary;
        private final AEKey planned;
        private final boolean shared;
        private long remaining;

        private CreditQuota(UUID beneficiary, AEKey planned, long remaining, boolean shared) {
            this.beneficiary = beneficiary;
            this.planned = planned;
            this.remaining = remaining;
            this.shared = shared;
        }
    }

    private static long saturatingSignedAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        // NBT stores the magnitude in GenericStack, whose valid maximum is Long.MAX_VALUE.
        // Keeping the negative bound symmetric also matches variant-debt saturation exactly.
        if (right < 0 && (right == Long.MIN_VALUE
                || left < -Long.MAX_VALUE - right)) return -Long.MAX_VALUE;
        return left + right;
    }
}
