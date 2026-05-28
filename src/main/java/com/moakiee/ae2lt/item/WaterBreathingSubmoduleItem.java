package com.moakiee.ae2lt.item;

import java.util.List;

import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.overload.armor.ArmorOverloadRules;
import com.moakiee.ae2lt.overload.armor.ArmorPart;
import com.moakiee.ae2lt.overload.armor.module.WaterBreathingSubmodule;

import net.minecraft.world.effect.MobEffects;

public final class WaterBreathingSubmoduleItem extends AbstractSingleArmorSubmoduleItem {

    public WaterBreathingSubmoduleItem(Properties properties) {
        super(
                properties.stacksTo(64),
                ArmorPart.HEAD,
                WaterBreathingSubmodule.INSTANCE,
                "item.ae2lt.module_water_breathing.tooltip",
                stack -> List.of(
                        new DeviceCapability.StatusEffectGrant(MobEffects.WATER_BREATHING, 0),
                        new DeviceCapability.PassiveDrain(ArmorOverloadRules.WATER_BREATHING_PASSIVE_DRAIN_FE)));
    }
}
