package com.moakiee.ae2lt.overload.model;

/**
 * Slot-local matching rule used by overload patterns.
 * <p>
 * This enum is intentionally pattern-local. It does not alter AE2's global key
 * semantics and must only be consulted by overload-aware code paths.
 */
public enum MatchMode {
    /**
     * Compare item id and all attached NBT.
     */
    STRICT,

    /**
     * Compare item id only and ignore attached NBT.
     */
    ID_ONLY;

    public boolean ignoresComponents() {
        return this == ID_ONLY;
    }
}
