package com.moakiee.ae2lt.overload.armor.module;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;

public final class WaterBreathingSubmodule extends AbstractOverloadArmorSubmodule {

    public static final WaterBreathingSubmodule INSTANCE = new WaterBreathingSubmodule();

    private WaterBreathingSubmodule() {}

    @Override
    public String id() {
        return "water_breathing";
    }

    @Override
    public String nameKey() {
        return "ae2lt.overload_armor.feature.water_breathing.name";
    }

    @Override
    public String descriptionKey() {
        return "ae2lt.overload_armor.feature.water_breathing.desc";
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
        if (player != null && dist == Dist.DEDICATED_SERVER) {
            player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, Integer.MAX_VALUE, 0, false, false, true));
        }
    }

    @Override
    public void onDeactivated(@Nullable Player player, Dist dist, ItemStack armor) {
        if (player != null && dist == Dist.DEDICATED_SERVER) {
            player.removeEffect(MobEffects.WATER_BREATHING);
        }
    }

    @Override
    public int tickActive(@Nullable Player player, Dist dist, ItemStack armor) {
        return 0;
    }
}
