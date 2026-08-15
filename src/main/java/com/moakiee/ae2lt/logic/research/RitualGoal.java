package com.moakiee.ae2lt.logic.research;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;

public enum RitualGoal {
    HYPERDIMENSIONAL_PIGMEE;

    public String translationKey() {
        return "ae2lt.research_note.goal." + name().toLowerCase(Locale.ROOT);
    }

    public Component getDisplayName() {
        return Component.translatable(translationKey());
    }

    public static @Nullable RitualGoal fromName(String name) {
        try {
            return valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
