package com.moakiee.ae2lt.celestweave.module;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;

public final class ResistanceSubmodule extends AbstractCelestweaveArmorSubmodule {

    public static final ResistanceSubmodule T1 = new ResistanceSubmodule(
            "matrix_shield",
            "ae2lt.celestweave.feature.matrix_shield.name",
            "ae2lt.celestweave.feature.matrix_shield.desc");
    public static final ResistanceSubmodule T2 = new ResistanceSubmodule(
            "phase_shield",
            "ae2lt.celestweave.feature.phase_shield.name",
            "ae2lt.celestweave.feature.phase_shield.desc");

    public static final String INSTALL_GROUP = "mitigation";
    public static final String HIT_FEEDBACK_CONFIG_KEY = "hit_feedback";

    private final String id;
    private final String nameKey;
    private final String descriptionKey;

    private ResistanceSubmodule(String id, String nameKey, String descriptionKey) {
        this.id = id;
        this.nameKey = nameKey;
        this.descriptionKey = descriptionKey;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String nameKey() {
        return nameKey;
    }

    @Override
    public String descriptionKey() {
        return descriptionKey;
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
    public String installGroupId() {
        return INSTALL_GROUP;
    }

    @Override
    public void onActivated(@Nullable Player player, Dist dist, ItemStack armor) {
        // Resistance is applied via StagedMitigation in CelestweaveArmorDamageHandler.
    }

    @Override
    public void onDeactivated(@Nullable Player player, Dist dist, ItemStack armor) {
        // No persistent effect to remove; damage handler checks active state.
    }

    @Override
    public List<CelestweaveArmorSubmoduleConfig> getConfigs(ItemStack armor) {
        return List.of(hitFeedbackConfig(armor));
    }

    @Override
    public boolean setConfig(ItemStack armor, String key, @Nullable Tag value) {
        if (!HIT_FEEDBACK_CONFIG_KEY.equals(key)) {
            return false;
        }
        var options = getOptions(armor);
        options.put(HIT_FEEDBACK_CONFIG_KEY, value instanceof ByteTag byteTag
                ? byteTag
                : ByteTag.valueOf(true));
        setOptions(armor, options);
        return true;
    }

    @Override
    public int tickActive(@Nullable Player player, Dist dist, ItemStack armor) {
        return 0;
    }

    public static boolean isHitFeedbackEnabled(ItemStack armor, String stage) {
        return submoduleForStage(stage).isHitFeedbackEnabled(armor);
    }

    private CelestweaveArmorSubmoduleConfig hitFeedbackConfig(ItemStack armor) {
        return config(
                HIT_FEEDBACK_CONFIG_KEY,
                Component.translatable("ae2lt.celestweave.config.hit_feedback"),
                ByteTag.valueOf(isHitFeedbackEnabled(armor)),
                booleanChoices(),
                null);
    }

    private boolean isHitFeedbackEnabled(ItemStack armor) {
        var options = getOptions(armor);
        if (!options.contains(HIT_FEEDBACK_CONFIG_KEY, Tag.TAG_BYTE)) {
            return true;
        }
        return options.getBoolean(HIT_FEEDBACK_CONFIG_KEY);
    }

    private static ResistanceSubmodule submoduleForStage(String stage) {
        return "phase_shield".equals(stage) ? T2 : T1;
    }
}
