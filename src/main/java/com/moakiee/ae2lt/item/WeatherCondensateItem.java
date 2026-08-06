package com.moakiee.ae2lt.item;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.moakiee.ae2lt.logic.WeatherControlHelper;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class WeatherCondensateItem extends Item {
    private final Type type;

    public WeatherCondensateItem(Type type, Properties properties) {
        super(properties);
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    @Nullable
    public static Type getType(ItemStack stack) {
        if (stack.getItem() instanceof WeatherCondensateItem condensateItem) {
            return condensateItem.getType();
        }
        return null;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            List<Component> tooltipComponents,
            TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable(
                "item.ae2lt.weather_condensate.target",
                type.getWeatherName()).withStyle(ChatFormatting.AQUA));
        tooltipComponents.add(Component.translatable(
                "item.ae2lt.weather_condensate.energy",
                type.totalEnergy()).withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable(
                "item.ae2lt.weather_condensate.duration",
                type.minDuration(),
                type.maxDuration()).withStyle(ChatFormatting.DARK_GRAY));
    }

    public enum Type implements StringRepresentable {
        CLEAR("clear", "ae2lt.weather.clear"),
        RAIN("rain", "ae2lt.weather.rain"),
        THUNDERSTORM("thunderstorm", "ae2lt.weather.thunderstorm");

        private static final int CLEAR_ENERGY = 500_000;
        private static final int RAIN_ENERGY = 1_000_000;
        private static final int THUNDERSTORM_ENERGY = 8_000_000;
        private static final int CLEAR_DURATION_MIN = 12_000;
        private static final int CLEAR_DURATION_MAX = 180_000;
        private static final int RAIN_DURATION_MIN = 12_000;
        private static final int RAIN_DURATION_MAX = 24_000;
        private static final int THUNDERSTORM_DURATION_MIN = 3_600;
        private static final int THUNDERSTORM_DURATION_MAX = 15_600;

        private final String serializedName;
        private final String weatherTranslationKey;

        Type(String serializedName, String weatherTranslationKey) {
            this.serializedName = serializedName;
            this.weatherTranslationKey = weatherTranslationKey;
        }

        public Component getWeatherName() {
            return Component.translatable(weatherTranslationKey);
        }

        public long totalEnergy() {
            return switch (this) {
                case CLEAR -> CLEAR_ENERGY;
                case RAIN -> RAIN_ENERGY;
                case THUNDERSTORM -> THUNDERSTORM_ENERGY;
            };
        }

        public int minDuration() {
            return switch (this) {
                case CLEAR -> CLEAR_DURATION_MIN;
                case RAIN -> RAIN_DURATION_MIN;
                case THUNDERSTORM -> THUNDERSTORM_DURATION_MIN;
            };
        }

        public int maxDuration() {
            return switch (this) {
                case CLEAR -> CLEAR_DURATION_MAX;
                case RAIN -> RAIN_DURATION_MAX;
                case THUNDERSTORM -> THUNDERSTORM_DURATION_MAX;
            };
        }

        public boolean isActive(ServerLevel level) {
            return switch (this) {
                case CLEAR -> !level.isRaining() && !level.isThundering();
                case RAIN -> level.isRaining() && !level.isThundering();
                case THUNDERSTORM -> level.isRaining() && level.isThundering();
            };
        }

        public boolean apply(ServerLevel level, RandomSource random) {
            if (!WeatherControlHelper.supportsWeather(level)) {
                return false;
            }

            int duration = WeatherControlHelper.rollDuration(random, minDuration(), maxDuration());
            switch (this) {
                case CLEAR -> WeatherControlHelper.setClearWeather(level, duration);
                case RAIN -> WeatherControlHelper.setRainWeather(level, duration);
                case THUNDERSTORM -> WeatherControlHelper.setThunderstorm(level, duration);
            }
            return true;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }

        public static Type fromOrdinal(int ordinal) {
            Type[] values = values();
            if (ordinal < 0 || ordinal >= values.length) {
                return CLEAR;
            }
            return values[ordinal];
        }

        @Nullable
        public static Type fromName(String name) {
            for (Type value : values()) {
                if (value.serializedName.equals(name)) {
                    return value;
                }
            }
            return null;
        }
    }
}
