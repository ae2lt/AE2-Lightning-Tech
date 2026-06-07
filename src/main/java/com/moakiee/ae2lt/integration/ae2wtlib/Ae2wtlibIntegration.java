package com.moakiee.ae2lt.integration.ae2wtlib;

import com.moakiee.ae2lt.registry.ModItems;

import de.mari_023.ae2wtlib.api.registration.UpgradeHelper;

/**
 * Soft-optional ae2wtlib integration entry point.
 *
 * <p>This class references ae2wtlib API types directly, so it must only be
 * class-loaded when ae2wtlib is present. Callers gate access behind
 * {@code ModList.get().isLoaded("ae2wtlib")}.</p>
 */
public final class Ae2wtlibIntegration {

    private Ae2wtlibIntegration() {
    }

    /**
     * Registers the overloaded frequency card as a one-slot upgrade for every
     * ae2wtlib wireless terminal (and the universal terminal). If ae2wtlib has
     * not finished its own upgrade registration yet, {@link UpgradeHelper}
     * queues this and applies it once it is ready.
     */
    public static void register() {
        UpgradeHelper.addUpgradeToAllTerminals(ModItems.OVERLOADED_FREQUENCY_CARD.get(), 1);
    }
}
