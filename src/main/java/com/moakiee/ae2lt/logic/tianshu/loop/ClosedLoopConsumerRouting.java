package com.moakiee.ae2lt.logic.tianshu.loop;

import appeng.api.stacks.AEKey;
import com.moakiee.thunderbolt.core.planner.Sat;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Compiles ordered member seed transitions into consumer-owned accounts.
 *
 * <p>A routed amount belongs only to the member that will consume it. The runtime therefore does
 * not need to retain its producer identity: a producer merely credits the fixed consumer accounts
 * in its {@link ProducerRouting}. Matching walks forward from each producer and wraps at the end of
 * the member list. Wrapped credits are the bootstrap state that must exist before the first cycle.
 *
 * <p>Keys remain exact planned {@link AEKey}s here. In particular, this compiler does not collapse
 * component-bearing keys with {@link AEKey#dropSecondary()}. Fuzzy and overloaded execution may
 * re-key the credited physical stack after the accepting member resolves its actual variant, while
 * the stable consumer id and planned route remain unchanged.
 */
public final class ClosedLoopConsumerRouting {
    private static final String CONSUMER_ID_PREFIX = "ae2lt:closed-loop-consumer:v1:";

    /**
     * Builds a fixed route without expanding any member copy count.
     *
     * @throws IllegalArgumentException if the ordered seed outputs cannot exactly satisfy all
     *     ordered seed inputs for one cycle
     */
    public static RoutingPlan compile(
            UUID groupId, List<ClosedLoopPatternAnalyzer.MemberFlow> orderedFlows) {
        Objects.requireNonNull(groupId, "groupId");
        Objects.requireNonNull(orderedFlows, "orderedFlows");
        if (orderedFlows.isEmpty()) {
            throw new IllegalArgumentException("a closed-loop route requires at least one member");
        }

        var flows = List.copyOf(orderedFlows);
        var consumerIds = new ArrayList<UUID>(flows.size());
        var bootstrapByConsumer = new ArrayList<LinkedHashMap<AEKey, Long>>(flows.size());
        var routesByProducer = new ArrayList<LinkedHashMap<UUID, LinkedHashMap<AEKey, Long>>>(
                flows.size());
        var wrappedRoutesByProducer =
                new ArrayList<LinkedHashMap<UUID, LinkedHashMap<AEKey, Long>>>(flows.size());
        var keys = new LinkedHashSet<AEKey>();
        for (int memberIndex = 0; memberIndex < flows.size(); memberIndex++) {
            var flow = Objects.requireNonNull(flows.get(memberIndex),
                    "orderedFlows[" + memberIndex + "]");
            validateAmounts(flow.inputSeed(), "inputSeed", memberIndex, keys);
            validateAmounts(flow.outputSeed(), "outputSeed", memberIndex, keys);
            consumerIds.add(consumerId(groupId, memberIndex));
            bootstrapByConsumer.add(new LinkedHashMap<>());
            routesByProducer.add(new LinkedHashMap<>());
            wrappedRoutesByProducer.add(new LinkedHashMap<>());
        }

        for (var key : keys) {
            routeKey(flows, consumerIds, bootstrapByConsumer,
                    routesByProducer, wrappedRoutesByProducer, key);
        }

        var consumers = new ArrayList<ConsumerAccount>(flows.size());
        var producers = new ArrayList<ProducerRouting>(flows.size());
        var bootstrapSeed = new LinkedHashMap<AEKey, Long>();
        for (int memberIndex = 0; memberIndex < flows.size(); memberIndex++) {
            var consumerBootstrap = immutableMap(bootstrapByConsumer.get(memberIndex));
            consumers.add(new ConsumerAccount(
                    memberIndex, consumerIds.get(memberIndex), consumerBootstrap));
            merge(bootstrapSeed, consumerBootstrap);
            producers.add(new ProducerRouting(
                    memberIndex,
                    immutableNestedMap(routesByProducer.get(memberIndex)),
                    immutableNestedMap(wrappedRoutesByProducer.get(memberIndex))));
        }
        return new RoutingPlan(
                groupId, consumers, producers, immutableMap(bootstrapSeed));
    }

    private static void routeKey(
            List<ClosedLoopPatternAnalyzer.MemberFlow> flows,
            List<UUID> consumerIds,
            List<LinkedHashMap<AEKey, Long>> bootstrapByConsumer,
            List<LinkedHashMap<UUID, LinkedHashMap<AEKey, Long>>> routesByProducer,
            List<LinkedHashMap<UUID, LinkedHashMap<AEKey, Long>>> wrappedRoutesByProducer,
            AEKey key) {
        var remainingDemand = new long[flows.size()];
        for (int consumerIndex = 0; consumerIndex < flows.size(); consumerIndex++) {
            remainingDemand[consumerIndex] = positiveAmount(
                    flows.get(consumerIndex).inputSeed(), key);
        }

        for (int producerIndex = 0; producerIndex < flows.size(); producerIndex++) {
            long remainingOutput = positiveAmount(flows.get(producerIndex).outputSeed(), key);
            int consumerIndex = (producerIndex + 1) % flows.size();
            for (int visited = 0; remainingOutput > 0L && visited < flows.size(); visited++) {
                long amount = Math.min(remainingOutput, remainingDemand[consumerIndex]);
                if (amount > 0L) {
                    addRoute(routesByProducer.get(producerIndex),
                            consumerIds.get(consumerIndex), key, amount);
                    remainingDemand[consumerIndex] -= amount;
                    remainingOutput -= amount;
                    if (consumerIndex <= producerIndex) {
                        add(bootstrapByConsumer.get(consumerIndex), key, amount);
                        addRoute(wrappedRoutesByProducer.get(producerIndex),
                                consumerIds.get(consumerIndex), key, amount);
                    }
                }
                consumerIndex = (consumerIndex + 1) % flows.size();
            }
            if (remainingOutput > 0L) {
                throw unbalanced(key, "unmatched output", remainingOutput);
            }
        }

        for (long remaining : remainingDemand) {
            if (remaining > 0L) {
                throw unbalanced(key, "unmatched input", remaining);
            }
        }
    }

    private static void validateAmounts(
            Map<AEKey, Long> amounts,
            String label,
            int memberIndex,
            LinkedHashSet<AEKey> keys) {
        Objects.requireNonNull(amounts, label + " for member " + memberIndex);
        for (var entry : amounts.entrySet()) {
            var key = Objects.requireNonNull(entry.getKey(),
                    label + " key for member " + memberIndex);
            var boxedAmount = Objects.requireNonNull(entry.getValue(),
                    label + " amount for member " + memberIndex);
            if (boxedAmount < 0L) {
                throw new IllegalArgumentException(
                        label + " amount must not be negative for member " + memberIndex);
            }
            if (boxedAmount > 0L) keys.add(key);
        }
    }

    private static long positiveAmount(Map<AEKey, Long> amounts, AEKey key) {
        return Math.min(Sat.SAT, Math.max(0L, amounts.getOrDefault(key, 0L)));
    }

    private static void addRoute(
            LinkedHashMap<UUID, LinkedHashMap<AEKey, Long>> producerRoutes,
            UUID consumerId,
            AEKey key,
            long amount) {
        add(producerRoutes.computeIfAbsent(consumerId, ignored -> new LinkedHashMap<>()),
                key, amount);
    }

    private static void merge(
            LinkedHashMap<AEKey, Long> target, Map<AEKey, Long> amounts) {
        for (var entry : amounts.entrySet()) add(target, entry.getKey(), entry.getValue());
    }

    private static void add(
            LinkedHashMap<AEKey, Long> target, AEKey key, long amount) {
        if (amount <= 0L) return;
        target.merge(key, amount, Sat::add);
    }

    private static IllegalArgumentException unbalanced(
            AEKey key, String kind, long amount) {
        return new IllegalArgumentException(
                "unbalanced closed-loop seed flow for " + key + ": " + kind + " " + amount);
    }

    private static UUID consumerId(UUID groupId, int memberIndex) {
        return UUID.nameUUIDFromBytes((CONSUMER_ID_PREFIX + groupId + ':' + memberIndex)
                .getBytes(StandardCharsets.UTF_8));
    }

    private static <K, V> Map<K, V> immutableMap(Map<K, V> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static Map<UUID, Map<AEKey, Long>> immutableNestedMap(
            Map<UUID, ? extends Map<AEKey, Long>> source) {
        var copy = new LinkedHashMap<UUID, Map<AEKey, Long>>();
        for (var entry : source.entrySet()) {
            copy.put(entry.getKey(), immutableMap(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    public record RoutingPlan(
            UUID groupId,
            List<ConsumerAccount> consumers,
            List<ProducerRouting> producers,
            Map<AEKey, Long> bootstrapSeed) {
        public RoutingPlan {
            Objects.requireNonNull(groupId, "groupId");
            consumers = List.copyOf(consumers);
            producers = List.copyOf(producers);
            bootstrapSeed = immutableMap(bootstrapSeed);
        }
    }

    public record ConsumerAccount(
            int memberIndex,
            UUID consumerId,
            Map<AEKey, Long> bootstrapSeed) {
        public ConsumerAccount {
            if (memberIndex < 0) throw new IllegalArgumentException("memberIndex must be positive");
            Objects.requireNonNull(consumerId, "consumerId");
            bootstrapSeed = immutableMap(bootstrapSeed);
        }
    }

    public record ProducerRouting(
            int memberIndex,
            Map<UUID, Map<AEKey, Long>> targets,
            Map<UUID, Map<AEKey, Long>> wrappedTargets) {
        public ProducerRouting {
            if (memberIndex < 0) throw new IllegalArgumentException("memberIndex must be positive");
            targets = immutableNestedMap(targets);
            wrappedTargets = immutableNestedMap(wrappedTargets);
        }
    }

    private ClosedLoopConsumerRouting() {
    }
}
