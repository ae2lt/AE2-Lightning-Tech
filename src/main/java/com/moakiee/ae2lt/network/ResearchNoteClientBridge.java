package com.moakiee.ae2lt.network;

import java.util.Objects;

import net.minecraft.world.item.ItemStack;

public final class ResearchNoteClientBridge {
    private static Hooks hooks = Hooks.NOOP;

    private ResearchNoteClientBridge() {
    }

    public static void install(Hooks hooks) {
        ResearchNoteClientBridge.hooks = Objects.requireNonNull(hooks);
    }

    public static void open(ItemStack book) {
        hooks.open(book);
    }

    public interface Hooks {
        Hooks NOOP = new Hooks() {
        };

        default void open(ItemStack book) {
        }
    }
}
