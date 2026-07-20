package com.moakiee.ae2lt.device.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

import com.moakiee.ae2lt.device.capability.DeviceCapability;

class LightningCompensationPolicyTest {
    @Test
    void usesAvailableEhvBeforeCompensatingTheShortfall() {
        var plan = LightningCompensationPolicy.plan(3L, 16L, 131L, 8L, 16);

        assertTrue(plan.canPay());
        assertEquals(8L, plan.extremeHighVoltageToConsume());
        assertEquals(128L, plan.compensatedHighVoltage());
        assertEquals(131L, plan.highVoltageToConsume());
    }

    @Test
    void refusesMissingEhvWithoutAnInstalledCompensationCapability() {
        var plan = LightningCompensationPolicy.plan(0L, 16L, Long.MAX_VALUE, 15L, 0);

        assertFalse(plan.canPay());
    }

    @Test
    void refusesAnInsufficientCombinedHvPayment() {
        var plan = LightningCompensationPolicy.plan(1L, 16L, 128L, 8L, 16);

        assertFalse(plan.canPay());
        assertEquals(129L, plan.highVoltageToConsume());
    }

    @Test
    void selectsTheMostEfficientInstalledCompensationCapability() {
        int ratio = LightningCompensationPolicy.bestRatio(List.of(
                new DeviceCapability.LightningCompensation(32),
                new DeviceCapability.LightningCompensation(16),
                new DeviceCapability.PassiveDrain(1L)));

        assertEquals(16, ratio);
    }

    @Test
    void saturatesOverflowInsteadOfWrappingTheCompensationCost() {
        assertEquals(Long.MAX_VALUE, LightningCompensationPolicy.highVoltageRequired(Long.MAX_VALUE, 16));
        assertFalse(LightningCompensationPolicy.plan(
                        0L,
                        Long.MAX_VALUE,
                        Long.MAX_VALUE,
                        0L,
                        16)
                .canPay());
    }
}
