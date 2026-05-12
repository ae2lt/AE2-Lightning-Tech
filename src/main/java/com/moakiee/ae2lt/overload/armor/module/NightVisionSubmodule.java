package com.moakiee.ae2lt.overload.armor.module;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;

public final class NightVisionSubmodule extends AbstractOverloadArmorSubmodule {

    public static final NightVisionSubmodule INSTANCE = new NightVisionSubmodule();

    private static final int IDLE_LOAD = 4;

    private NightVisionSubmodule() {}

    @Override
    public String id() {
        return "night_vision";
    }

    @Override
    public String nameKey() {
        return "ae2lt.overload_armor.feature.night_vision.name";
    }

    @Override
    public String descriptionKey() {
        return "ae2lt.overload_armor.feature.night_vision.desc";
    }

    @Override
    public boolean defaultEnabled() {
        return true;
    }

    @Override
    public int getIdleOverloaded(@Nullable Player player, Dist dist, ItemStack armor) {
        return IDLE_LOAD;
    }

    @Override
    public void onActivated(@Nullable Player player, Dist dist, ItemStack armor) {
        if (player != null && dist == Dist.DEDICATED_SERVER) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false, true));
        }
    }

    @Override
    public void onDeactivated(@Nullable Player player, Dist dist, ItemStack armor) {
        if (player != null && dist == Dist.DEDICATED_SERVER) {
            player.removeEffect(MobEffects.NIGHT_VISION);
        }
    }

    @Override
    public int tickActive(@Nullable Player player, Dist dist, ItemStack armor) {
        return 0;
    }
}
