package com.moakiee.ae2lt.grid.wirelesslink;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WirelessClusterEntrancePlannerTest {
    private final Map<IGridNode, NodeState> states = new IdentityHashMap<>();

    @Test
    void choosesOneCarryingEntranceForMultipleMissingDevices() {
        var primary = node("primary", false, false);
        var branch = node("branch", false, false);
        var missingA = node("missing-a", true, false);
        var missingB = node("missing-b", true, false);
        connect(primary, branch);
        connect(branch, missingA);
        connect(branch, missingB);

        var candidate = WirelessClusterEntrancePlanner.findSupplementalEntrance(
                cluster(primary, branch, missingA, missingB),
                cluster(primary));

        assertSame(branch, candidate);
    }

    @Test
    void skipsAnEntranceThatAlreadyExists() {
        var primary = node("primary", false, false);
        var usedBranch = node("used-branch", false, false);
        var nextBranch = node("next-branch", false, false);
        var missing = node("missing", true, false);
        connect(primary, usedBranch);
        connect(usedBranch, nextBranch);
        connect(nextBranch, missing);

        var candidate = WirelessClusterEntrancePlanner.findSupplementalEntrance(
                cluster(primary, usedBranch, nextBranch, missing),
                cluster(primary, usedBranch));

        assertSame(nextBranch, candidate);
    }

    @Test
    void directlyLinksAnIsolatedNonCarryingDeviceAsFallback() {
        var missing = node("missing", true, false);
        states.get(missing).flags.add(GridFlags.CANNOT_CARRY);

        var candidate = WirelessClusterEntrancePlanner.findSupplementalEntrance(
                cluster(missing),
                cluster());

        assertSame(missing, candidate);
    }

    @Test
    void ninthDeviceWithoutAChannelGetsAnotherEntrance() {
        var primary = node("primary", false, false);
        var nodes = new ArrayList<IGridNode>();
        nodes.add(primary);
        for (int i = 0; i < 8; i++) {
            var active = node("active-" + i, true, true);
            connect(primary, active);
            nodes.add(active);
        }
        var ninth = node("ninth", true, false);
        connect(primary, ninth);
        nodes.add(ninth);

        var candidate = WirelessClusterEntrancePlanner.findSupplementalEntrance(
                cluster(nodes.toArray(IGridNode[]::new)),
                cluster(primary));

        assertSame(ninth, candidate);
    }

    @Test
    void doesNothingWhenEveryDeviceHasAChannel() {
        var active = node("active", true, true);

        assertNull(WirelessClusterEntrancePlanner.findSupplementalEntrance(
                cluster(active),
                cluster()));
    }

    private IGridNode node(String name, boolean requiresChannel, boolean meetsChannelRequirements) {
        var state = new NodeState(meetsChannelRequirements);
        if (requiresChannel) {
            state.flags.add(GridFlags.REQUIRE_CHANNEL);
        }
        var node = (IGridNode) Proxy.newProxyInstance(
                IGridNode.class.getClassLoader(),
                new Class<?>[] {IGridNode.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getConnections" -> state.connections;
                    case "hasFlag" -> state.flags.contains(args[0]);
                    case "meetsChannelRequirements" -> state.meetsChannelRequirements;
                    case "toString" -> name;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> defaultValue(method.getReturnType());
                });
        states.put(node, state);
        return node;
    }

    private void connect(IGridNode a, IGridNode b) {
        var connection = (IGridConnection) Proxy.newProxyInstance(
                IGridConnection.class.getClassLoader(),
                new Class<?>[] {IGridConnection.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getOtherSide" -> {
                        if (args[0] == a) yield b;
                        if (args[0] == b) yield a;
                        throw new IllegalArgumentException("not an endpoint");
                    }
                    case "isInWorld" -> true;
                    case "a" -> a;
                    case "b" -> b;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> defaultValue(method.getReturnType());
                });
        states.get(a).connections.add(connection);
        states.get(b).connections.add(connection);
    }

    private static Set<IGridNode> cluster(IGridNode... nodes) {
        var result = Collections.newSetFromMap(new IdentityHashMap<IGridNode, Boolean>());
        Collections.addAll(result, nodes);
        return result;
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0.0F;
        if (type == double.class) return 0.0D;
        if (type == char.class) return '\0';
        throw new IllegalArgumentException("Unsupported primitive: " + type);
    }

    private static final class NodeState {
        private final List<IGridConnection> connections = new ArrayList<>();
        private final EnumSet<GridFlags> flags = EnumSet.noneOf(GridFlags.class);
        private final boolean meetsChannelRequirements;

        private NodeState(boolean meetsChannelRequirements) {
            this.meetsChannelRequirements = meetsChannelRequirements;
        }
    }
}
