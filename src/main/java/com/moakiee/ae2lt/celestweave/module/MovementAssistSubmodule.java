package com.moakiee.ae2lt.celestweave.module;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class MovementAssistSubmodule extends AbstractCelestweaveArmorSubmodule {
    public static final MovementAssistSubmodule INSTANCE = new MovementAssistSubmodule();

    public static final String WALK_SPEED_CONFIG_KEY = "walk_speed_multiplier";
    public static final String SPRINT_SPEED_CONFIG_KEY = "sprint_speed_multiplier";
    public static final String SNEAK_SPEED_CONFIG_KEY = "sneak_speed_multiplier";
    public static final String STEP_HEIGHT_CONFIG_KEY = "automatic_step_height";

    private MovementAssistSubmodule() {
    }

    @Override
    public String id() {
        return "movement_assist";
    }

    @Override
    public String nameKey() {
        return "ae2lt.celestweave.feature.movement_assist.name";
    }

    @Override
    public String descriptionKey() {
        return "ae2lt.celestweave.feature.movement_assist.desc";
    }

    @Override
    public boolean defaultEnabled() {
        return true;
    }

    @Override
    public int getMaxInstallAmount() {
        return 1;
    }

    @Override
    public List<CelestweaveArmorSubmoduleConfig> getConfigs(ItemStack armor) {
        return List.of(
                speedConfig(armor, WALK_SPEED_CONFIG_KEY),
                speedConfig(armor, SPRINT_SPEED_CONFIG_KEY),
                speedConfig(armor, SNEAK_SPEED_CONFIG_KEY),
                stepHeightConfig(armor));
    }

    @Override
    public boolean setConfig(ItemStack armor, String key, @Nullable Tag value) {
        var options = getOptions(armor);
        if (isSpeedConfig(key)) {
            options.put(key, MovementSpeedOption.fromTag(value).toTag());
        } else if (STEP_HEIGHT_CONFIG_KEY.equals(key)) {
            options.put(key, StepHeightOption.fromTag(value).toTag());
        } else {
            return false;
        }
        setOptions(armor, options);
        return true;
    }

    public static double walkSpeedMultiplier(ItemStack armor) {
        return selectedSpeed(armor, WALK_SPEED_CONFIG_KEY).multiplier();
    }

    public static double sprintSpeedMultiplier(ItemStack armor) {
        return selectedSpeed(armor, SPRINT_SPEED_CONFIG_KEY).multiplier();
    }

    public static double sneakSpeedMultiplier(ItemStack armor) {
        return selectedSpeed(armor, SNEAK_SPEED_CONFIG_KEY).multiplier();
    }

    public static double automaticStepHeight(ItemStack armor) {
        var options = INSTANCE.getOptions(armor);
        return StepHeightOption.fromTag(options.get(STEP_HEIGHT_CONFIG_KEY)).height();
    }

    private CelestweaveArmorSubmoduleConfig speedConfig(ItemStack armor, String key) {
        MovementSpeedOption selected = selectedSpeed(armor, key);
        return config(
                key,
                Component.translatable("ae2lt.celestweave.config." + key),
                selected.toTag(),
                speedChoices(),
                Component.translatable("ae2lt.celestweave.config." + key + ".hint"));
    }

    private CelestweaveArmorSubmoduleConfig stepHeightConfig(ItemStack armor) {
        var selected = StepHeightOption.fromTag(getOptions(armor).get(STEP_HEIGHT_CONFIG_KEY));
        return config(
                STEP_HEIGHT_CONFIG_KEY,
                Component.translatable("ae2lt.celestweave.config." + STEP_HEIGHT_CONFIG_KEY),
                selected.toTag(),
                stepHeightChoices(),
                Component.translatable("ae2lt.celestweave.config." + STEP_HEIGHT_CONFIG_KEY + ".hint"));
    }

    private List<CelestweaveArmorSubmoduleConfigChoice> speedChoices() {
        return java.util.Arrays.stream(MovementSpeedOption.values())
                .map(option -> choice(
                        option.toTag(),
                        Component.translatable("ae2lt.celestweave.config.value.multiplier", option.label())))
                .toList();
    }

    private List<CelestweaveArmorSubmoduleConfigChoice> stepHeightChoices() {
        return java.util.Arrays.stream(StepHeightOption.values())
                .map(option -> choice(
                        option.toTag(),
                        Component.translatable("ae2lt.celestweave.config.value.blocks", option.label())))
                .toList();
    }

    private static MovementSpeedOption selectedSpeed(ItemStack armor, String key) {
        var options = INSTANCE.getOptions(armor);
        return MovementSpeedOption.fromTag(options.get(key));
    }

    private static boolean isSpeedConfig(String key) {
        return WALK_SPEED_CONFIG_KEY.equals(key)
                || SPRINT_SPEED_CONFIG_KEY.equals(key)
                || SNEAK_SPEED_CONFIG_KEY.equals(key);
    }
}
