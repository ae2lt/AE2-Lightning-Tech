package com.moakiee.ae2lt.celestweave;

import java.util.List;
import java.util.Map;

import com.moakiee.ae2lt.AE2LightningTech;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CelestweaveArmorMaterials {
    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, AE2LightningTech.MODID);

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> CELESTWEAVE =
            ARMOR_MATERIALS.register(
                    "celestweave",
                    () -> new ArmorMaterial(
                            Map.of(
                                    ArmorItem.Type.HELMET, 6,
                                    ArmorItem.Type.CHESTPLATE, 12,
                                    ArmorItem.Type.LEGGINGS, 8,
                                    ArmorItem.Type.BOOTS, 5),
                            0,
                            SoundEvents.ARMOR_EQUIP_GENERIC,
                            () -> Ingredient.EMPTY,
                            List.of(),
                            5.0F,
                            0.2F));

    private CelestweaveArmorMaterials() {
    }
}
