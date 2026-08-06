package com.moakiee.ae2lt.client.core;

final class CoreEffectAnimationState {
    private static final double RESPONSE_PER_SECOND = 6.0D;
    private static final double AMBIENT_PERIOD_SECONDS = Math.PI * 4.0D;

    private double lastRenderTick = Double.NaN;
    private double activity;
    private double primaryPhase;
    private double secondaryPhase;
    private double glowPhase;
    private double ambientTime;

    Sample sample(double renderTick, boolean working, MotionProfile profile) {
        double targetActivity = working ? 1.0D : 0.0D;
        if (Double.isNaN(lastRenderTick)) {
            double worldSeconds = renderTick / 20.0D;
            activity = targetActivity;
            primaryPhase = wrap(
                    worldSeconds * rate(profile.primaryIdleRate(), profile.primaryWorkingRate(), activity),
                    profile.primaryPeriod());
            secondaryPhase = wrap(
                    worldSeconds * rate(profile.secondaryIdleRate(), profile.secondaryWorkingRate(), activity),
                    profile.secondaryPeriod());
            glowPhase = wrap(
                    worldSeconds * rate(profile.glowIdleRate(), profile.glowWorkingRate(), activity),
                    Math.PI * 2.0D);
            ambientTime = wrap(worldSeconds, AMBIENT_PERIOD_SECONDS);
            lastRenderTick = renderTick;
            return snapshot();
        }

        double elapsedSeconds = (renderTick - lastRenderTick) / 20.0D;
        lastRenderTick = renderTick;
        if (elapsedSeconds <= 0.0D) {
            return snapshot();
        }

        double previousActivity = activity;
        double decay = Math.exp(-RESPONSE_PER_SECOND * elapsedSeconds);
        activity = targetActivity + (previousActivity - targetActivity) * decay;
        double averageActivity = targetActivity
                + (previousActivity - targetActivity)
                * (1.0D - decay)
                / (RESPONSE_PER_SECOND * elapsedSeconds);

        primaryPhase = advance(
                primaryPhase,
                elapsedSeconds * rate(
                        profile.primaryIdleRate(),
                        profile.primaryWorkingRate(),
                        averageActivity),
                profile.primaryPeriod());
        secondaryPhase = advance(
                secondaryPhase,
                elapsedSeconds * rate(
                        profile.secondaryIdleRate(),
                        profile.secondaryWorkingRate(),
                        averageActivity),
                profile.secondaryPeriod());
        glowPhase = advance(
                glowPhase,
                elapsedSeconds * rate(
                        profile.glowIdleRate(),
                        profile.glowWorkingRate(),
                        averageActivity),
                Math.PI * 2.0D);
        ambientTime = advance(ambientTime, elapsedSeconds, AMBIENT_PERIOD_SECONDS);
        return snapshot();
    }

    private Sample snapshot() {
        return new Sample(activity, primaryPhase, secondaryPhase, glowPhase, ambientTime);
    }

    private static double rate(double idleRate, double workingRate, double activity) {
        return idleRate + (workingRate - idleRate) * activity;
    }

    private static double advance(double phase, double amount, double period) {
        return wrap(phase + amount, period);
    }

    private static double wrap(double value, double period) {
        double wrapped = value % period;
        return wrapped < 0.0D ? wrapped + period : wrapped;
    }

    record MotionProfile(
            double primaryIdleRate,
            double primaryWorkingRate,
            double primaryPeriod,
            double secondaryIdleRate,
            double secondaryWorkingRate,
            double secondaryPeriod,
            double glowIdleRate,
            double glowWorkingRate) {
    }

    record Sample(
            double activity,
            double primaryPhase,
            double secondaryPhase,
            double glowPhase,
            double ambientTime) {
    }
}
