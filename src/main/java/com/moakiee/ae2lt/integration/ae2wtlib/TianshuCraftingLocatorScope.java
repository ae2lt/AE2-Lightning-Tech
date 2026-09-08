package com.moakiee.ae2lt.integration.ae2wtlib;

/** Limits the extra terminal predicate to AE2WTLib's crafting-enhancement locator. */
public final class TianshuCraftingLocatorScope {
    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);
    private TianshuCraftingLocatorScope() {}
    public static boolean active() { return ACTIVE.get(); }
    public static boolean enter() { boolean previous = ACTIVE.get(); ACTIVE.set(true); return previous; }
    public static void restore(boolean previous) { if (previous) ACTIVE.set(true); else ACTIVE.remove(); }
}
