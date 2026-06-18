package com.moakiee.ae2lt.celestweave.module;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;

public record CelestweaveArmorSubmoduleOptionUi(
        String key,
        Component label,
        Component value,
        boolean editable,
        @Nullable Component hint,
        Kind kind
) {
    /**
     * Backward-compatible constructor that infers a generic kind. New call sites should pass a
     * specific {@link Kind} so the screen can render type-appropriate controls.
     */
    public CelestweaveArmorSubmoduleOptionUi(
            String key,
            Component label,
            Component value,
            boolean editable,
            @Nullable Component hint
    ) {
        this(key, label, value, editable, hint, editable ? Kind.CYCLE : Kind.READ_ONLY);
    }

    /**
     * Type-hint for the renderer so it can pick an appropriate inline control:
     * <ul>
     *   <li>{@link #BOOLEAN} – draws an ON/OFF pill the user can click to flip
     *   <li>{@link #CYCLE} – draws {@code ◀ value ▶} arrows the user can click to step through choices
     *   <li>{@link #READ_ONLY} – draws only the value text; uneditable
     * </ul>
     */
    public enum Kind {
        BOOLEAN,
        CYCLE,
        READ_ONLY
    }
}
