package com.moakiee.ae2lt.celestweave;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class MovementAssistRulesTest {
    private static final double EPSILON = 1.0E-9D;

    @Test
    void selectsMultiplierForCurrentGroundMovementState() {
        assertEquals(1.5D, multiplier(false, false), EPSILON);
        assertEquals(3.0D, multiplier(false, true), EPSILON);
        assertEquals(0.5D, multiplier(true, false), EPSILON);
        assertEquals(0.5D, multiplier(true, true), EPSILON, "sneaking takes priority over sprint state");
    }

    @Test
    void suppressesGroundSpeedAssistDuringFlightAndSwimming() {
        assertEquals(
                1.0D,
                MovementAssistRules.movementMultiplier(true, false, true, 1.5D, 3.0D, 0.5D),
                EPSILON);
    }

    @Test
    void producesMultiplicativeSpeedModifierAmounts() {
        assertEquals(-0.5D, MovementAssistRules.speedModifierAmount(0.5D), EPSILON);
        assertEquals(0.0D, MovementAssistRules.speedModifierAmount(1.0D), EPSILON);
        assertEquals(3.0D, MovementAssistRules.speedModifierAmount(4.0D), EPSILON);
    }

    @Test
    void convertsConfiguredTotalStepHeightIntoVanillaRelativeBonus() {
        assertEquals(0.0D, MovementAssistRules.stepHeightModifierAmount(0.6D), EPSILON);
        assertEquals(0.9D, MovementAssistRules.stepHeightModifierAmount(1.5D), EPSILON);
        assertEquals(2.4D, MovementAssistRules.stepHeightModifierAmount(3.0D), EPSILON);
    }

    @Test
    void invalidValuesFallBackToVanillaBehavior() {
        assertEquals(0.0D, MovementAssistRules.speedModifierAmount(Double.NaN), EPSILON);
        assertEquals(0.0D, MovementAssistRules.stepHeightModifierAmount(-1.0D), EPSILON);
    }

    private static double multiplier(boolean crouching, boolean sprinting) {
        return MovementAssistRules.movementMultiplier(
                false,
                crouching,
                sprinting,
                1.5D,
                3.0D,
                0.5D);
    }
}
