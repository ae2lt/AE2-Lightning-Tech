package com.moakiee.ae2lt.util;

import java.lang.reflect.Field;

import net.minecraft.world.inventory.Slot;

/**
 * In 1.20.1 the Slot.x and Slot.y fields are still final (they became mutable
 * in 1.21). For pre-1.21 we have to drop the final modifier via reflection so
 * we can shuffle slot positions when sub-screens reuse layout coordinates.
 */
public final class SlotPositionAccess {
    // At runtime Minecraft classes are SRG-remapped (f_40220_/f_40221_), while
    // dev environments expose the MCP names (x/y), so try both spellings.
    private static final Field X = locate("x", "f_40220_");
    private static final Field Y = locate("y", "f_40221_");

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

    private static Field locate(String name, String srgName) {
        for (var candidate : new String[] { name, srgName }) {
            try {
                Field f = Slot.class.getDeclaredField(candidate);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                // Try the next candidate.
            }
        }
        throw new ExceptionInInitializerError(
                new NoSuchFieldException("Slot." + name + " / " + srgName));
    }
}
