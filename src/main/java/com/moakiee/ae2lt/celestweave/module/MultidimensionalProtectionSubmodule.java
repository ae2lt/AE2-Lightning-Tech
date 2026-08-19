package com.moakiee.ae2lt.celestweave.module;

import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Free, combined Phase Shield and Undying behavior for the multidimensional tier. */
public final class MultidimensionalProtectionSubmodule extends AbstractCelestweaveArmorSubmodule {
    public static final MultidimensionalProtectionSubmodule INSTANCE =
            new MultidimensionalProtectionSubmodule();
    public static final String ID = "multidimensional_protection";

    private MultidimensionalProtectionSubmodule() {
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String nameKey() {
        return "ae2lt.celestweave.feature.multidimensional_protection.name";
    }

    @Override
    public String descriptionKey() {
        return "ae2lt.celestweave.feature.multidimensional_protection.desc";
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
    public Set<String> installGroupIds() {
        return Set.of(ResistanceSubmodule.INSTALL_GROUP, UndyingSubmodule.INSTANCE.installGroupId());
    }

    @Override
    public List<CelestweaveArmorSubmoduleConfig> getConfigs(ItemStack armor) {
        return List.of(config(
                ResistanceSubmodule.HIT_FEEDBACK_CONFIG_KEY,
                Component.translatable("ae2lt.celestweave.config.hit_feedback"),
                ByteTag.valueOf(isHitFeedbackEnabled(armor)),
                booleanChoices(),
                null));
    }

    @Override
    public boolean setConfig(ItemStack armor, String key, @Nullable Tag value) {
        if (!ResistanceSubmodule.HIT_FEEDBACK_CONFIG_KEY.equals(key)) {
            return false;
        }
        var options = getOptions(armor);
        options.put(ResistanceSubmodule.HIT_FEEDBACK_CONFIG_KEY, value instanceof ByteTag byteTag
                ? byteTag
                : ByteTag.valueOf(true));
        setOptions(armor, options);
        return true;
    }

    public static boolean isHitFeedbackEnabled(ItemStack armor) {
        var options = INSTANCE.getOptions(armor);
        if (!options.contains(ResistanceSubmodule.HIT_FEEDBACK_CONFIG_KEY, Tag.TAG_BYTE)) {
            return true;
        }
        return options.getBoolean(ResistanceSubmodule.HIT_FEEDBACK_CONFIG_KEY);
    }
}
