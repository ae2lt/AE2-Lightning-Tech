package com.moakiee.ae2lt.logic;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public final class WeatherControlHelper {
    private WeatherControlHelper() {
    }

    public static boolean supportsWeather(ServerLevel level) {
        return level.dimensionType().hasSkyLight() && !level.dimensionType().hasCeiling();
    }

    public static int rollDuration(RandomSource random, int minDuration, int maxDuration) {
        int clampedMin = Math.max(1, minDuration);
        int clampedMax = Math.max(clampedMin, maxDuration);
        if (clampedMin == clampedMax) {
            return clampedMin;
        }
        return Mth.nextInt(random, clampedMin, clampedMax);
    }

    public static void setClearWeather(ServerLevel level, int duration) {
        level.setWeatherParameters(Math.max(1, duration), 0, false, false);
    }

    public static void setRainWeather(ServerLevel level, int duration) {
        level.setWeatherParameters(0, Math.max(1, duration), true, false);
    }

    public static void setThunderstorm(ServerLevel level, int duration) {
        level.setWeatherParameters(0, Math.max(1, duration), true, true);
    }
}
