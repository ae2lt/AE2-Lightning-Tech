package com.moakiee.ae2lt.celestweave.module;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;

public final class PurificationSubmodule extends AbstractCelestweaveArmorSubmodule {

    public static final PurificationSubmodule INSTANCE = new PurificationSubmodule();

    private PurificationSubmodule() {
    }

    @Override
    public String id() {
        return "purification";
    }

    @Override
    public String nameKey() {
        return "ae2lt.celestweave.feature.purification.name";
    }

    @Override
    public String descriptionKey() {
        return "ae2lt.celestweave.feature.purification.desc";
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
