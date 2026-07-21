package com.moakiee.ae2lt.item.railgun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.moakiee.ae2lt.device.DeviceKind;
import com.moakiee.ae2lt.device.DeviceSlotType;
import com.moakiee.ae2lt.device.capability.DeviceCapability;

class RailgunRangeModuleTest {

    @Test
    void installsAtMostTwoRangeModulesOnTheRailgun() {
        assertEquals(2, RailgunModuleItem.maxInstallAmount(RailgunModuleType.RANGE));
        assertTrue(RailgunModuleItem.accepts(
                RailgunModuleType.RANGE,
                DeviceKind.RAILGUN,
                DeviceSlotType.RANGE));
        assertFalse(RailgunModuleItem.accepts(
                RailgunModuleType.RANGE,
                DeviceKind.CELESTWEAVE_CORE,
                DeviceSlotType.CHEST_MODULE));
    }

    @Test
    void eachInstalledUnitContributesOneTwoTimesMultiplier() {
        var capabilities = RailgunModuleItem.capabilitiesFor(RailgunModuleType.RANGE);
        assertEquals(1, capabilities.size());
        var range = assertInstanceOf(DeviceCapability.RangeMultiplier.class, capabilities.getFirst());
        assertEquals(2.0D, range.factor());
    }
}
