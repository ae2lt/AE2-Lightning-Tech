package com.moakiee.ae2lt.celestweave.phase;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

/** Pure version arbitration for the phase-lock projection mirror. */
public final class PhaseLockProjectionSyncRules {
    private PhaseLockProjectionSyncRules() {
    }

    public enum Direction {
        NONE,
        ARMOR_TO_PROJECTION,
        PROJECTION_TO_ARMOR
    }

    public static Direction direction(
            UUID armorId,
            long armorUpdate,
            @Nullable PhaseLockProjectionLink projectionLink,
            boolean mirroredFieldsEqual) {
        if (projectionLink == null
                || !armorId.equals(projectionLink.armorId())
                || projectionLink.update() < armorUpdate) {
            return Direction.ARMOR_TO_PROJECTION;
        }
        if (mirroredFieldsEqual) {
            return Direction.NONE;
        }
        return Direction.PROJECTION_TO_ARMOR;
    }

    public static long nextUpdate(long armorUpdate, @Nullable PhaseLockProjectionLink projectionLink) {
        long projectionUpdate = projectionLink == null ? 0L : projectionLink.update();
        long current = Math.max(0L, Math.max(armorUpdate, projectionUpdate));
        return current == Long.MAX_VALUE ? Long.MAX_VALUE : current + 1L;
    }
}
