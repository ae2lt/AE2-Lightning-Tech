package com.moakiee.ae2lt.item;

import java.util.List;

import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.celestweave.ArmorOverloadRules;
import com.moakiee.ae2lt.celestweave.ArmorPart;
import com.moakiee.ae2lt.celestweave.module.NightVisionSubmodule;

import net.minecraft.world.effect.MobEffects;

public final class NightVisionSubmoduleItem extends AbstractSingleArmorSubmoduleItem {

    public NightVisionSubmoduleItem(Properties properties) {
        super(
                properties,
                ArmorPart.HEAD,
                NightVisionSubmodule.INSTANCE,
                stack -> List.of(
                        new DeviceCapability.StatusEffectGrant(MobEffects.NIGHT_VISION, 0),
                        new DeviceCapability.PassiveDrain(ArmorOverloadRules.NIGHT_VISION_PASSIVE_DRAIN_FE)));
    }
}
