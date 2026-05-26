package com.moakiee.ae2lt.overload.armor.module;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;

public final class ReflectSubmodule extends AbstractOverloadArmorSubmodule {

    public static final ReflectSubmodule INSTANCE = new ReflectSubmodule();

    private ReflectSubmodule() {}

    @Override
    public String id() {
        return "reflect";
    }

    @Override
    public String nameKey() {
        return "ae2lt.overload_armor.feature.reflect.name";
    }

    @Override
    public String descriptionKey() {
        return "ae2lt.overload_armor.feature.reflect.desc";
    }

    @Override
    public boolean defaultEnabled() {
        return true;
    }

    @Override
    public int getIdleOverloaded(@Nullable Player player, Dist dist, ItemStack armor) {
        return 0;
    }

    @Override
    public void onActivated(@Nullable Player player, Dist dist, ItemStack armor) {
        // Reflect is handled by OverloadArmorDamageHandler reading ReflectTuning.
    }

    @Override
    public void onDeactivated(@Nullable Player player, Dist dist, ItemStack armor) {
    }

    @Override
    public int tickActive(@Nullable Player player, Dist dist, ItemStack armor) {
        return 0;
    }
}
