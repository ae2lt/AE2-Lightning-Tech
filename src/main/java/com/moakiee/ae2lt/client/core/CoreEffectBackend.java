package com.moakiee.ae2lt.client.core;

/**
 * Core-effect shader backend selection.
 *
 * <p>1.20.1 port note: the NeoForge 1.21 Veil integration (version range [4.3.0,5.0.0),
 * {@code VeilRenderBridge}) does not exist on Forge 1.20.1 — Veil 1.20.1 is 1.x with a
 * different API and no local jar is available. The native shader backend
 * ({@link CoreEffectShaders}) is therefore always used; this class only exists to keep
 * the previous backend-selection call sites intact.</p>
 */
final class CoreEffectBackend {

    private CoreEffectBackend() {
    }

    /** Always false on 1.20.1 Forge — see class comment. */
    static boolean useVeil() {
        return false;
    }

    /** Kept for compatibility with call sites; the native backend needs no fallback. */
    static void disableVeil(Throwable cause) {
    }
}
