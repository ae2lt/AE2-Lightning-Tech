package com.moakiee.ae2lt.overload.armor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.moakiee.ae2lt.overload.armor.state.ArmorRuntimeRegistry;

final class ArmorRuntimeRegistryTest {

    @Test
    void serverAndClientCachesStaySeparateAndClearDropsRuntimeState() {
        UUID armorId = UUID.randomUUID();
        ArmorRuntimeRegistry.clear(armorId);

        ArmorRuntimeRegistry.setServerSubmoduleActive(armorId, "flight", true);
        assertTrue(ArmorRuntimeRegistry.isServerSubmoduleActive(armorId, "flight"));
        assertFalse(ArmorRuntimeRegistry.isClientSubmoduleActive(armorId, "flight"));

        ArmorRuntimeRegistry.setClientSubmoduleActive(armorId, "flight", true);
        assertTrue(ArmorRuntimeRegistry.isClientSubmoduleActive(armorId, "flight"));
        assertTrue(ArmorRuntimeRegistry.isAnyClientSubmoduleActive("flight"));

        ArmorRuntimeRegistry.setSubmoduleRuntimeActive(armorId, "flight", true);
        ArmorRuntimeRegistry.setSubmoduleRuntimeDynamicLoad(armorId, "flight", 12);
        assertTrue(ArmorRuntimeRegistry.getSubmoduleRuntime(armorId, "flight").active());
        assertEquals(12, ArmorRuntimeRegistry.getSubmoduleRuntime(armorId, "flight").dynamicLoad());

        ArmorRuntimeRegistry.clear(armorId);
        assertFalse(ArmorRuntimeRegistry.isServerSubmoduleActive(armorId, "flight"));
        assertFalse(ArmorRuntimeRegistry.isClientSubmoduleActive(armorId, "flight"));
        assertFalse(ArmorRuntimeRegistry.getSubmoduleRuntime(armorId, "flight").active());
        assertEquals(0, ArmorRuntimeRegistry.getSubmoduleRuntime(armorId, "flight").dynamicLoad());
    }
}
