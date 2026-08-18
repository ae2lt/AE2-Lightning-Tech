package com.moakiee.ae2lt.util;

import java.lang.reflect.Field;

import net.minecraft.world.inventory.Slot;

/**
 * In 1.20.1 the Slot.x and Slot.y fields are still final (they became mutable
 * in 1.21). For pre-1.21 we have to drop the final modifier via reflection so
 * we can shuffle slot positions when sub-screens reuse layout coordinates.
 */
public final class SlotPositionAccess {
    private static final Field X = locate("x");
    private static final Field Y = locate("y");

    private SlotPositionAccess() {
    }

    public static void set(Slot slot, int x, int y) {
        try {
            X.setInt(slot, x);
            Y.setInt(slot, y);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to update slot position", e);
        }
    }

    private static Field locate(String name) {
        try {
            Field f = Slot.class.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
