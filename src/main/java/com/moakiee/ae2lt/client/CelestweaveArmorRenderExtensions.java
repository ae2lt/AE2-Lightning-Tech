package com.moakiee.ae2lt.client;

import org.jetbrains.annotations.NotNull;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/** Implements main's empty armor-material layers on Forge 1.20.1. */
public final class CelestweaveArmorRenderExtensions implements IClientItemExtensions {
    public static final CelestweaveArmorRenderExtensions INSTANCE =
            new CelestweaveArmorRenderExtensions();

    private CelestweaveArmorRenderExtensions() {
    }

    @Override
    public @NotNull HumanoidModel<?> getHumanoidArmorModel(
            LivingEntity livingEntity,
            ItemStack itemStack,
            EquipmentSlot equipmentSlot,
            HumanoidModel<?> original) {
        original.setAllVisible(false);
        return original;
    }
}
