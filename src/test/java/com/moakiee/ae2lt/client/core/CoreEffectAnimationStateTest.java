package com.moakiee.ae2lt.client.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreEffectAnimationStateTest {
    private static final double EPSILON = 1.0E-9D;
    private static final CoreEffectAnimationState.MotionProfile PROFILE =
            new CoreEffectAnimationState.MotionProfile(
                    2.0D, 10.0D, 1_000.0D,
                    4.0D, 20.0D, 1_000.0D,
                    1.0D, 5.0D);

    @Test
    void changingWorkingStateDoesNotChangeTheCurrentPhase() {
        var state = new CoreEffectAnimationState();
        var idle = state.sample(200.0D, false, PROFILE);
        var switched = state.sample(200.0D, true, PROFILE);

        assertEquals(idle.primaryPhase(), switched.primaryPhase(), EPSILON);
        assertEquals(idle.secondaryPhase(), switched.secondaryPhase(), EPSILON);
        assertEquals(idle.glowPhase(), switched.glowPhase(), EPSILON);
    }

    @Test
    void workingStateAcceleratesSmoothlyInsteadOfImmediately() {
        var state = new CoreEffectAnimationState();
        var before = state.sample(0.0D, false, PROFILE);
        var after = state.sample(1.0D, true, PROFILE);

        double primaryAdvance = after.primaryPhase() - before.primaryPhase();
        assertTrue(after.activity() > 0.0D && after.activity() < 1.0D);
        assertTrue(primaryAdvance > PROFILE.primaryIdleRate() / 20.0D);
        assertTrue(primaryAdvance < PROFILE.primaryWorkingRate() / 20.0D);
    }

    @Test
    void decelerationAlsoKeepsThePhaseContinuous() {
        var state = new CoreEffectAnimationState();
        state.sample(0.0D, true, PROFILE);
        var before = state.sample(20.0D, true, PROFILE);
        var after = state.sample(21.0D, false, PROFILE);

        double primaryAdvance = after.primaryPhase() - before.primaryPhase();
        assertTrue(after.activity() > 0.0D && after.activity() < 1.0D);
        assertTrue(primaryAdvance > PROFILE.primaryIdleRate() / 20.0D);
        assertTrue(primaryAdvance < PROFILE.primaryWorkingRate() / 20.0D);
    }
}
