package com.moakiee.ae2lt.celestweave.module;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;

public final class DigAffinitySubmodule extends AbstractCelestweaveArmorSubmodule {

    public static final DigAffinitySubmodule INSTANCE = new DigAffinitySubmodule();

    private DigAffinitySubmodule() {
    }

    @Override
    public String id() {
        return "dig_affinity";
    }

    @Override
    public String nameKey() {
        return "ae2lt.celestweave.feature.dig_affinity.name";
    }

    @Override
    public String descriptionKey() {
        return "ae2lt.celestweave.feature.dig_affinity.desc";
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
