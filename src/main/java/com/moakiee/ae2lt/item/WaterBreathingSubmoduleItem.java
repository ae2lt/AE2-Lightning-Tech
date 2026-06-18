package com.moakiee.ae2lt.item;

import java.util.List;

import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.celestweave.ArmorOverloadRules;
import com.moakiee.ae2lt.celestweave.ArmorPart;
import com.moakiee.ae2lt.celestweave.module.WaterBreathingSubmodule;

import net.minecraft.world.effect.MobEffects;

public final class WaterBreathingSubmoduleItem extends AbstractSingleArmorSubmoduleItem {

    public WaterBreathingSubmoduleItem(Properties properties) {
        super(
                properties,
                ArmorPart.HEAD,
                WaterBreathingSubmodule.INSTANCE,
                stack -> List.of(
                        new DeviceCapability.StatusEffectGrant(MobEffects.WATER_BREATHING, 0),
                        new DeviceCapability.PassiveDrain(ArmorOverloadRules.WATER_BREATHING_PASSIVE_DRAIN_FE)));
    }
}
