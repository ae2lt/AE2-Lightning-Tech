package com.moakiee.ae2lt.overload.armor.module;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;

public final class CleanseSubmodule extends AbstractOverloadArmorSubmodule {

    public static final CleanseSubmodule INSTANCE = new CleanseSubmodule();

    private CleanseSubmodule() {
    }

    @Override
    public String id() {
        return "cleanse";
    }

    @Override
    public String nameKey() {
        return "ae2lt.overload_armor.feature.cleanse.name";
    }

    @Override
    public String descriptionKey() {
        return "ae2lt.overload_armor.feature.cleanse.desc";
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
    public int tickActive(@Nullable Player player, Dist dist, ItemStack armor) {
        return 0;
    }
}
