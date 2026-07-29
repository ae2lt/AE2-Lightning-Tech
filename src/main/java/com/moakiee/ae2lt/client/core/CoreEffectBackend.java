package com.moakiee.ae2lt.client.core;

import net.neoforged.fml.ModList;

final class CoreEffectBackend {
    private static final boolean VEIL_LOADED = ModList.get().isLoaded("veil");

    private CoreEffectBackend() {
    }

    static boolean useVeil() {
        return VEIL_LOADED;
    }
}
