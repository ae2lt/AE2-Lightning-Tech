package com.moakiee.ae2lt.grid.wirelesslink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PhysicalGridClusterTest {
    private final Map<IGridNode, List<IGridConnection>> connections = new IdentityHashMap<>();

    @AfterEach
    void clearBridgeTracking() {
        WirelessLinkOps.clearWirelessBridgeTracking();
    }

    @Test
    void followsNonWorldAe2InternalConnections() {
        var a = node("A");
        var b = node("B");
        var c = node("C");
        connect(a, b, false);
        connect(b, c, false);

        var cluster = PhysicalGridCluster.collect(a);

        assertEquals(3, cluster.size());
        assertTrue(cluster.contains(a));
        assertTrue(cluster.contains(b));
        assertTrue(cluster.contains(c));
    }

    @Test
    void wirelessBridgeSeparatesPhysicalClusters() {
        var a = node("A");
        var b = node("B");
        var c = node("C");
        connect(a, b, true);
        var bridge = connect(b, c, false);
        WirelessLinkOps.trackWirelessBridge(bridge);

        var left = PhysicalGridCluster.collect(a);
        var right = PhysicalGridCluster.collect(c);

        assertEquals(2, left.size());
        assertTrue(left.contains(a));
        assertTrue(left.contains(b));
        assertFalse(left.contains(c));
        assertEquals(1, right.size());
        assertTrue(right.contains(c));
    }

    @Test
    void handlesCyclesOncePerNode() {
        var a = node("A");
        var b = node("B");
        var c = node("C");
        connect(a, b, true);
        connect(b, c, true);
        connect(c, a, true);

        assertEquals(3, PhysicalGridCluster.collect(a).size());
    }

    @Test
    void reusesExistingVirtualConnectionByEndpointIdentity() {
        var target = node("target");
        var transmitter = node("transmitter");
        var existing = connect(target, transmitter, false);

        var result = WirelessLinkOps.createVirtualConnection(target, transmitter);

        assertSame(existing, result);
        assertTrue(WirelessLinkOps.isWirelessBridge(existing));
        assertEquals(1, connections.get(target).size());
        assertEquals(1, connections.get(transmitter).size());
    }

    @Test
    void rejectsExistingPhysicalConnectionBetweenSameEndpoints() {
        var target = node("target");
        var transmitter = node("transmitter");
        var physical = connect(target, transmitter, true);

        assertThrows(
                IllegalStateException.class,
                () -> WirelessLinkOps.createVirtualConnection(target, transmitter));
        assertFalse(WirelessLinkOps.isWirelessBridge(physical));
        assertEquals(1, connections.get(target).size());
        assertEquals(1, connections.get(transmitter).size());
    }

    @Test
    void middleRemovalSeedsBothSurvivingComponents() {
        var a = node("A");
        var b = node("B");
        var c = node("C");
        var ab = connect(a, b, true);
        var bc = connect(b, c, true);

        var survivingNeighbours = PhysicalGridCluster.directNeighbours(List.of(b));
        assertEquals(2, survivingNeighbours.size());
        assertTrue(survivingNeighbours.contains(a));
        assertTrue(survivingNeighbours.contains(c));

        disconnect(a, b, ab);
        disconnect(b, c, bc);

        assertEquals(1, PhysicalGridCluster.collect(a).size());
        assertEquals(1, PhysicalGridCluster.collect(c).size());
    }

    private IGridNode node(String name) {
        var node = (IGridNode) Proxy.newProxyInstance(
                IGridNode.class.getClassLoader(),
                new Class<?>[] {IGridNode.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getConnections" -> connections.getOrDefault(proxy, List.of());
                    case "toString" -> name;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> defaultValue(method.getReturnType());
                });
        connections.put(node, new ArrayList<>());
        return node;
    }

    private IGridConnection connect(IGridNode a, IGridNode b, boolean inWorld) {
        var connection = (IGridConnection) Proxy.newProxyInstance(
                IGridConnection.class.getClassLoader(),
                new Class<?>[] {IGridConnection.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getOtherSide" -> {
                        if (args[0] == a) yield b;
                        if (args[0] == b) yield a;
                        throw new IllegalArgumentException("not an endpoint");
                    }
                    case "isInWorld" -> inWorld;
                    case "a" -> a;
                    case "b" -> b;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> defaultValue(method.getReturnType());
                });
        connections.get(a).add(connection);
        connections.get(b).add(connection);
        return connection;
    }

    private void disconnect(IGridNode a, IGridNode b, IGridConnection connection) {
        connections.get(a).remove(connection);
        connections.get(b).remove(connection);
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
}
