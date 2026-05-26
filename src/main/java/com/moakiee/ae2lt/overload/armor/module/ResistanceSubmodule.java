package com.moakiee.ae2lt.overload.armor.module;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;

public final class ResistanceSubmodule extends AbstractOverloadArmorSubmodule {

    public static final ResistanceSubmodule INSTANCE = new ResistanceSubmodule();

    private ResistanceSubmodule() {}

    @Override
    public String id() {
        return "resistance";
    }

    @Override
    public String nameKey() {
        return "ae2lt.overload_armor.feature.resistance.name";
    }

    @Override
    public String descriptionKey() {
        return "ae2lt.overload_armor.feature.resistance.desc";
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
        // Resistance is applied via StagedMitigation in OverloadArmorDamageHandler.
    }

    @Override
    public void onDeactivated(@Nullable Player player, Dist dist, ItemStack armor) {
        // No persistent effect to remove; damage handler checks active state.
    }

    @Override
    public int tickActive(@Nullable Player player, Dist dist, ItemStack armor) {
        return 0;
    }
}
