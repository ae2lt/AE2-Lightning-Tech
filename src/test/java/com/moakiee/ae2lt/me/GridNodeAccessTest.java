package com.moakiee.ae2lt.me;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;

class GridNodeAccessTest {
    @Test
    void treatsDestroyedBorrowedNodeAsUnavailable() {
        var gridCalls = new AtomicInteger();
        var node = node(false, null, true, gridCalls);

        assertNull(GridNodeAccess.getGridIfPresent(node));
        assertEquals(1, gridCalls.get());
    }

    @Test
    void inactiveCheckDoesNotTouchDestroyedNodesGrid() {
        var gridCalls = new AtomicInteger();
        var node = node(false, null, true, gridCalls);

        assertNull(GridNodeAccess.getActiveGrid(node));
        assertEquals(0, gridCalls.get());
    }

    @Test
    void returnsGridForActiveNode() {
        var gridCalls = new AtomicInteger();
        var grid = proxy(IGrid.class);
        var node = node(true, grid, false, gridCalls);

        assertSame(grid, GridNodeAccess.getActiveGrid(node));
        assertEquals(1, gridCalls.get());
    }

    private static IGridNode node(
            boolean active,
            IGrid grid,
            boolean throwOnGridAccess,
            AtomicInteger gridCalls) {
        return (IGridNode) Proxy.newProxyInstance(
                IGridNode.class.getClassLoader(),
                new Class<?>[] {IGridNode.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isActive" -> active;
                    case "getGrid" -> {
                        gridCalls.incrementAndGet();
                        if (throwOnGridAccess) {
                            throw new IllegalStateException(
                                    "A node is being used after it has been destroyed.");
                        }
                        yield grid;
                    }
                    case "toString" -> "test-grid-node";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> defaultValue(method.getReturnType());
                });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[] {type},
                (proxy, method, args) -> defaultValue(method.getReturnType()));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        throw new AssertionError("Unsupported primitive: " + type);
    }
}
