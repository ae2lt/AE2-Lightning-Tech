package com.moakiee.ae2lt.celestweave.module;

import java.util.Objects;

import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

public record CelestweaveArmorSubmoduleConfigChoice(
        Tag value,
        Component label
) {
    public CelestweaveArmorSubmoduleConfigChoice {
        value = Objects.requireNonNull(value, "value").copy();
        Objects.requireNonNull(label, "label");
    }

    public boolean matches(Tag candidate) {
        return value.equals(candidate);
    }
}
