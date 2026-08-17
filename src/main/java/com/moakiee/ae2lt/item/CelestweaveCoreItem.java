package com.moakiee.ae2lt.item;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.celestweave.ArmorPart;
import com.moakiee.ae2lt.celestweave.BaseCelestweaveArmorItem;
import com.moakiee.ae2lt.celestweave.PhaseWingFlight;

public final class CelestweaveCoreItem extends BaseCelestweaveArmorItem {
    public CelestweaveCoreItem(Properties properties) {
        super(ArmorPart.CHEST, properties);
    }

    @Override
    public boolean canElytraFly(ItemStack stack, LivingEntity entity) {
        return PhaseWingFlight.canElytraFly(entity);
    }

    @Override
    public boolean elytraFlightTick(ItemStack stack, LivingEntity entity, int flightTicks) {
        return PhaseWingFlight.elytraFlightTick(entity);
    }
}
