package com.moakiee.ae2lt.celestweave;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.world.phys.Vec3;

import org.junit.jupiter.api.Test;

final class PhaseWingFlightRulesTest {
    private static final double EPSILON = 1.0E-12D;

    @Test
    void fireworkThrustAcceleratesFromRestAlongLookDirection() {
        Vec3 result = PhaseWingFlight.fireworkThrust(
                Vec3.ZERO,
                new Vec3(1.0D, 0.0D, 0.0D),
                1.0D);

        assertEquals(0.85D, result.x, EPSILON);
        assertEquals(0.0D, result.y, EPSILON);
        assertEquals(0.0D, result.z, EPSILON);
    }

    @Test
    void fireworkThrustKeepsForwardAccelerationAtTargetSpeed() {
        Vec3 result = PhaseWingFlight.fireworkThrust(
                new Vec3(1.5D, 0.0D, 0.0D),
                new Vec3(1.0D, 0.0D, 0.0D),
                1.0D);

        assertEquals(1.6D, result.x, EPSILON);
    }

    @Test
    void fireworkThrustSteersExistingMotionTowardLookDirection() {
        Vec3 result = PhaseWingFlight.fireworkThrust(
                new Vec3(0.0D, 0.0D, 1.0D),
                new Vec3(1.0D, 0.0D, 0.0D),
                1.0D);

        assertEquals(0.85D, result.x, EPSILON);
        assertEquals(0.0D, result.y, EPSILON);
        assertEquals(0.5D, result.z, EPSILON);
    }

    @Test
    void configuredFlightSpeedScalesFireworkAccelerationAndTargetSpeed() {
        Vec3 result = PhaseWingFlight.fireworkThrust(
                Vec3.ZERO,
                new Vec3(1.0D, 0.0D, 0.0D),
                2.0D);

        assertEquals(1.7D, result.x, EPSILON);
        assertEquals(2.0D, PhaseWingFlight.thrustMultiplier(0.10F), EPSILON);
        assertEquals(4.0D, PhaseWingFlight.thrustMultiplier(0.20F), EPSILON);
        assertEquals(1.0D, PhaseWingFlight.thrustMultiplier(0.0F), EPSILON);
    }
}
