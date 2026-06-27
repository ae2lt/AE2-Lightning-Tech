package com.moakiee.ae2lt.celestweave.module;

import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

public record CelestweaveArmorSubmoduleConfig(
        String key,
        Component label,
        Tag value,
        List<CelestweaveArmorSubmoduleConfigChoice> choices,
        @Nullable Component hint
) {
    public CelestweaveArmorSubmoduleConfig {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(label, "label");
        value = Objects.requireNonNull(value, "value").copy();
        choices = List.copyOf(Objects.requireNonNullElse(choices, List.of()));
    }

    public boolean editable() {
        return !choices.isEmpty();
    }

    @Nullable
    public CelestweaveArmorSubmoduleConfigChoice currentChoice() {
        for (var choice : choices) {
            if (choice.matches(value)) {
                return choice;
            }
        }
        return null;
    }

    @Nullable
    public Tag nextValue() {
        return stepValue(1);
    }

    @Nullable
    public Tag previousValue() {
        return stepValue(-1);
    }

    @Nullable
    private Tag stepValue(int delta) {
        if (choices.isEmpty()) {
            return null;
        }

        int currentIndex = -1;
        for (int index = 0; index < choices.size(); index++) {
            if (choices.get(index).matches(value)) {
                currentIndex = index;
                break;
            }
        }

        int size = choices.size();
        int nextIndex;
        if (currentIndex < 0) {
            nextIndex = delta >= 0 ? 0 : size - 1;
        } else {
            nextIndex = Math.floorMod(currentIndex + delta, size);
        }
        return choices.get(nextIndex).value();
    }
}
