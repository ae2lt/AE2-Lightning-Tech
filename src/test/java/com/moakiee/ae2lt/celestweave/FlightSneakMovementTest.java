package com.moakiee.ae2lt.celestweave;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class FlightSneakMovementTest {
    @Test
    void onlyControlledHoverCrouchingEnablesPrecisionMovement() {
        assertTrue(FlightSneakMovement.isActive(true, true, false, false, true, true, false));
        assertTrue(FlightSneakMovement.isActive(true, true, false, false, false, true, true));
        assertFalse(FlightSneakMovement.isActive(true, true, false, false, false, true, false));
        assertFalse(FlightSneakMovement.isActive(true, true, false, false, true, false, false));
        assertFalse(FlightSneakMovement.isActive(false, true, false, false, true, true, false));
        assertFalse(FlightSneakMovement.isActive(true, false, false, false, true, true, false));
        assertFalse(FlightSneakMovement.isActive(true, true, true, false, true, true, false));
        assertFalse(FlightSneakMovement.isActive(true, true, false, true, true, true, false));
    }

    @Test
    void instantMovementMatchesSteadyGroundSneakingWithoutAccumulatingVelocity() {
        for (float walkingSpeed : new float[] {0.05F, 0.1F, 0.2F, 0.4F}) {
            for (double sneakFactor : new double[] {0.3D, 0.75D, 1.0D}) {
                double groundVelocity = 0.0D;
                double groundMovement = 0.0D;
                for (int tick = 0; tick < 200; tick++) {
                    groundMovement = groundVelocity + walkingSpeed * sneakFactor * 0.98D;
                    groundVelocity = groundMovement * 0.6D * 0.91D;
                }
                double hoverMovement = FlightSneakMovement.movementSpeed(walkingSpeed) * sneakFactor * 0.98D;
                assertEquals(groundMovement, hoverMovement, 1.0E-7D);
            }
        }
    }

}
