package com.moakiee.ae2lt.celestweave.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.device.capability.FlightKind;

final class ArmorModuleLightningPolicyTest {
    private static final long REACH_HV = 1L;
    private static final long FLIGHT_HV = 2L;
    private static final long PHASE_HV = 8L;

    private static final List<DeviceCapability> PHASE_FLIGHT = List.of(
            new DeviceCapability.FlightMode(FlightKind.CREATIVE),
            new DeviceCapability.PhaseTraversal(400_000L));

    @Test
    void phaseModuleFallsBackToCreativeFlightCostWhenPhaseModeIsNotInUse() {
        var hovering = ArmorModuleLightningPolicy.passiveCost(
                PHASE_FLIGHT, false, false, REACH_HV, FLIGHT_HV, PHASE_HV);
        var moving = ArmorModuleLightningPolicy.passiveCost(
                PHASE_FLIGHT, true, false, REACH_HV, FLIGHT_HV, PHASE_HV);

        assertEquals(FLIGHT_HV, hovering.highVoltage());
        assertEquals(FLIGHT_HV * 2L, moving.highVoltage());
    }

    @Test
    void activePhaseModeUsesPhaseCostInsteadOfStackingBothFlightCosts() {
        var active = ArmorModuleLightningPolicy.passiveCost(
                PHASE_FLIGHT, true, true, REACH_HV, FLIGHT_HV, PHASE_HV);

        assertEquals(PHASE_HV, active.highVoltage());
        assertEquals(0L, active.extremeHighVoltage());
    }
}
