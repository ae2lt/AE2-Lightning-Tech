package com.moakiee.ae2lt.client;

import org.jetbrains.annotations.NotNull;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/** Prevents the 1.20.1 fallback armor layer from drawing Celestweave head equipment. */
public final class CelestweaveHeadRenderExtensions implements IClientItemExtensions {
    public static final CelestweaveHeadRenderExtensions INSTANCE =
            new CelestweaveHeadRenderExtensions();

    private CelestweaveHeadRenderExtensions() {
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
