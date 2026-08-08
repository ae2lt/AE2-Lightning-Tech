package com.moakiee.ae2lt.celestweave;

import java.util.Map;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * 1.20.1 defines {@link ArmorMaterial} as an interface (1.21 turned it into a
 * registered record), so the celestweave material is implemented directly
 * instead of being registered through a DeferredRegister.
 */
public final class CelestweaveArmorMaterials {
    private static final Map<ArmorItem.Type, Integer> DEFENSE = Map.of(
            ArmorItem.Type.HELMET, 6,
            ArmorItem.Type.CHESTPLATE, 12,
            ArmorItem.Type.LEGGINGS, 8,
            ArmorItem.Type.BOOTS, 5);

    public static final ArmorMaterial CELESTWEAVE = new ArmorMaterial() {
        @Override
        public int getDurabilityForType(ArmorItem.Type type) {
            return 0;
        }

        @Override
        public int getDefenseForType(ArmorItem.Type type) {
            return DEFENSE.getOrDefault(type, 0);
        }

        @Override
        public int getEnchantmentValue() {
            return 0;
        }

        @Override
        public SoundEvent getEquipSound() {
            return SoundEvents.ARMOR_EQUIP_GENERIC;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.EMPTY;
        }

        @Override
        public String getName() {
            return "celestweave";
        }

        @Override
        public float getToughness() {
            return 5.0F;
        }

        @Override
        public float getKnockbackResistance() {
            return 0.2F;
        }
    };

    private CelestweaveArmorMaterials() {
    }
}
