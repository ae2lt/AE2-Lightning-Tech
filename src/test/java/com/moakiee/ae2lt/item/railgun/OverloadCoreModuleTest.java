package com.moakiee.ae2lt.item.railgun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.moakiee.ae2lt.device.DeviceKind;
import com.moakiee.ae2lt.device.DeviceSlotType;
import com.moakiee.ae2lt.device.energy.LightningCompensationPolicy;

class OverloadCoreModuleTest {
    @Test
    void installsInTheRailgunCoreSlotOrChestplateModuleSlot() {
        assertTrue(RailgunModuleItem.accepts(
                RailgunModuleType.CORE,
                DeviceKind.RAILGUN,
                DeviceSlotType.CORE));
        assertTrue(RailgunModuleItem.accepts(
                RailgunModuleType.CORE,
                DeviceKind.CELESTWEAVE_CORE,
                DeviceSlotType.CHEST_MODULE));
        assertFalse(RailgunModuleItem.accepts(
                RailgunModuleType.CORE,
                DeviceKind.CELESTWEAVE_OCULUS,
                DeviceSlotType.HEAD_MODULE));
        assertEquals(1, RailgunModuleItem.maxInstallAmount(RailgunModuleType.CORE));
    }

    @Test
    void declaresTheSharedSixteenToOneLightningCompensationCapability() {
        assertEquals(
                16,
                LightningCompensationPolicy.bestRatio(
                        RailgunModuleItem.capabilitiesFor(RailgunModuleType.CORE)));
    }
}
