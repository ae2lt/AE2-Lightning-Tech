package com.moakiee.ae2lt.item.railgun;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RailgunSettingsTest {
    @Test
    void playerTargetingRequiresItemOptInAndServerPermission() {
        assertTrue(new RailgunSettings(false, true, true, false, true, true).allowsPlayerTargets(true));
        assertFalse(new RailgunSettings(false, false, true, false, true, true).allowsPlayerTargets(true));
        assertFalse(new RailgunSettings(false, true, true, false, true, true).allowsPlayerTargets(false));
        assertFalse(new RailgunSettings(false, false, true, false, true, true).allowsPlayerTargets(false));
    }

    @Test
    void overloadRemovalDefaultsToNormalDeathAndCanBeSwitchedIndependently() {
        assertFalse(RailgunSettings.DEFAULT.forceOverloadRemoval());

        RailgunSettings forced = RailgunSettings.DEFAULT.withForceOverloadRemoval(true);
        assertTrue(forced.forceOverloadRemoval());
        assertFalse(forced.terrainDestruction());
        assertFalse(forced.pvp());
        assertTrue(forced.soundEnabled());
        assertTrue(forced.chainDamage());
    }

    @Test
    void chainDamageDefaultsOnAndCanBeSwitchedIndependently() {
        assertTrue(RailgunSettings.DEFAULT.chainDamage());

        RailgunSettings disabled = RailgunSettings.DEFAULT.withChainDamage(false);
        assertFalse(disabled.chainDamage());
        assertTrue(disabled.overloadImpactTargeting());
        assertFalse(disabled.forceOverloadRemoval());
    }
}
