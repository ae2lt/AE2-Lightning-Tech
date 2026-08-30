package com.moakiee.ae2lt.integration.ae2wtlib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import appeng.api.networking.IGridNode;
import org.junit.jupiter.api.Test;

import com.moakiee.ae2lt.integration.ae2wtlib.WirelessTerminalFrequencyLink.Resolution;
import com.moakiee.ae2lt.integration.ae2wtlib.WirelessTerminalFrequencyLink.RouteKind;

class WirelessTerminalFrequencyLinkTest {
    @Test
    void absentOrUnboundFrequencyDefersWithoutLookingUpANode() {
        for (int frequencyId : new int[] {-1, 0}) {
            var route = WirelessTerminalFrequencyLink.resolveRoute(frequencyId, id -> {
                throw new AssertionError("An unbound card must not query the frequency manager");
            });
            assertEquals(RouteKind.NO_FREQUENCY, route.kind());
            assertFalse(route.usesFrequencyRoute());
            assertFalse(route.isNetworkPowered());
            assertNull(route.node());
        }
    }

    @Test
    void boundButUnavailableFrequencyStillDefersToNativeConnection() {
        var lookups = new AtomicInteger();
        var route = WirelessTerminalFrequencyLink.resolveRoute(42, id -> {
            assertEquals(42, id);
            lookups.incrementAndGet();
            return null;
        });
        assertEquals(1, lookups.get());
        assertEquals(RouteKind.UNAVAILABLE, route.kind());
        assertFalse(route.usesFrequencyRoute());
        assertFalse(route.isNetworkPowered());
        assertNull(route.node());
    }

    @Test
    void resolvedButUnpoweredNodeDoesNotFallBackToNativeConnection() {
        var node = node(new AtomicBoolean(false), new AtomicInteger());
        var route = WirelessTerminalFrequencyLink.resolveRoute(42, id -> node);
        assertEquals(RouteKind.RESOLVED, route.kind());
        assertSame(node, route.node(), "Actionable-node routing must not filter out unpowered nodes");
        assertTrue(route.usesFrequencyRoute());
        assertFalse(route.isNetworkPowered());
    }

    @Test
    void poweredNodeUsesFrequencyRouteWithoutRequiringAnActiveChannelOrGridLookup() {
        var node = node(new AtomicBoolean(true), new AtomicInteger());
        var route = WirelessTerminalFrequencyLink.resolveRoute(42, id -> node);
        assertTrue(route.usesFrequencyRoute());
        assertTrue(route.isNetworkPowered());
    }

    @Test
    void nodeSelectionDoesNotProbePowerAndPowerIsNotCached() {
        var powered = new AtomicBoolean(true);
        var powerReads = new AtomicInteger();
        var node = node(powered, powerReads);
        var route = WirelessTerminalFrequencyLink.resolveRoute(42, id -> node);
        assertSame(node, route.node());
        assertTrue(route.usesFrequencyRoute());
        assertEquals(0, powerReads.get(), "getActionableNode must not introduce an extra power check");
        assertTrue(route.isNetworkPowered());
        powered.set(false);
        assertFalse(route.isNetworkPowered());
        assertEquals(2, powerReads.get());
    }

    @Test
    void subsequentResolutionDoesNotReuseAPreviousNode() {
        var node = node(new AtomicBoolean(true), new AtomicInteger());
        assertSame(node, WirelessTerminalFrequencyLink.resolveRoute(42, id -> node).node());
        assertEquals(RouteKind.UNAVAILABLE, WirelessTerminalFrequencyLink.resolveRoute(42, id -> null).kind());
    }

    @Test
    void legacyPowerHelperKeepsItsNullAndLiveNodeContract() {
        assertFalse(WirelessTerminalFrequencyLink.isNetworkPowered(null));
        assertFalse(WirelessTerminalFrequencyLink.isNetworkPowered(node(new AtomicBoolean(false), new AtomicInteger())));
        assertTrue(WirelessTerminalFrequencyLink.isNetworkPowered(node(new AtomicBoolean(true), new AtomicInteger())));
    }

    @Test
    void resolutionCannotContradictItsNodePresence() {
        var node = node(new AtomicBoolean(true), new AtomicInteger());
        assertThrows(IllegalArgumentException.class, () -> new Resolution(RouteKind.RESOLVED, null));
        assertThrows(IllegalArgumentException.class, () -> new Resolution(RouteKind.UNAVAILABLE, node));
        assertThrows(IllegalArgumentException.class, () -> new Resolution(RouteKind.NO_FREQUENCY, node));
    }

    private static IGridNode node(AtomicBoolean powered, AtomicInteger powerReads) {
        return (IGridNode) Proxy.newProxyInstance(IGridNode.class.getClassLoader(), new Class<?>[] {IGridNode.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isPowered" -> {
                        powerReads.incrementAndGet();
                        yield powered.get();
                    }
                    case "toString" -> "frequency-test-node";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new AssertionError("Unexpected node access: " + method.getName());
                });
    }
}
