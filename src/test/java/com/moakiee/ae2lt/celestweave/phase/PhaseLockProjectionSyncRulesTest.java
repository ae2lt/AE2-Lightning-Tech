package com.moakiee.ae2lt.celestweave.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class PhaseLockProjectionSyncRulesTest {
    private static final UUID ARMOR_ID = UUID.fromString("edc0e37a-51d2-47bb-8e1f-94fd2daeb528");

    @Test
    void missingOrForeignLinkRefreshesProjection() {
        assertEquals(
                PhaseLockProjectionSyncRules.Direction.ARMOR_TO_PROJECTION,
                PhaseLockProjectionSyncRules.direction(ARMOR_ID, 4L, null, false));
        assertEquals(
                PhaseLockProjectionSyncRules.Direction.ARMOR_TO_PROJECTION,
                PhaseLockProjectionSyncRules.direction(
                        ARMOR_ID,
                        4L,
                        new PhaseLockProjectionLink(UUID.randomUUID(), 99L),
                        false));
    }

    @Test
    void staleProjectionNeverOverwritesArmor() {
        assertEquals(
                PhaseLockProjectionSyncRules.Direction.ARMOR_TO_PROJECTION,
                PhaseLockProjectionSyncRules.direction(
                        ARMOR_ID,
                        8L,
                        new PhaseLockProjectionLink(ARMOR_ID, 7L),
                        false));
    }

    @Test
    void equalOrNewerChangedProjectionWritesBack() {
        assertEquals(
                PhaseLockProjectionSyncRules.Direction.PROJECTION_TO_ARMOR,
                PhaseLockProjectionSyncRules.direction(
                        ARMOR_ID,
                        8L,
                        new PhaseLockProjectionLink(ARMOR_ID, 8L),
                        false));
        assertEquals(
                PhaseLockProjectionSyncRules.Direction.PROJECTION_TO_ARMOR,
                PhaseLockProjectionSyncRules.direction(
                        ARMOR_ID,
                        8L,
                        new PhaseLockProjectionLink(ARMOR_ID, 10L),
                        false));
    }

    @Test
    void equalFieldsDoNotCausePerTickWrites() {
        assertEquals(
                PhaseLockProjectionSyncRules.Direction.NONE,
                PhaseLockProjectionSyncRules.direction(
                        ARMOR_ID,
                        8L,
                        new PhaseLockProjectionLink(ARMOR_ID, 8L),
                        true));
    }

    @Test
    void updateIsMonotonicAndSaturates() {
        assertEquals(12L, PhaseLockProjectionSyncRules.nextUpdate(
                8L,
                new PhaseLockProjectionLink(ARMOR_ID, 11L)));
        assertEquals(Long.MAX_VALUE, PhaseLockProjectionSyncRules.nextUpdate(
                Long.MAX_VALUE,
                new PhaseLockProjectionLink(ARMOR_ID, Long.MAX_VALUE)));
    }
}
