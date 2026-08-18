package com.moakiee.ae2lt.item;

import java.util.List;

import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.celestweave.ArmorOverloadRules;
import com.moakiee.ae2lt.celestweave.ArmorPart;
import com.moakiee.ae2lt.celestweave.module.NightVisionSubmodule;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffects;

public final class NightVisionSubmoduleItem extends AbstractSingleArmorSubmoduleItem {

    public NightVisionSubmoduleItem(Properties properties) {
        super(
                properties,
                ArmorPart.HEAD,
                NightVisionSubmodule.INSTANCE,
                stack -> List.of(
                        // 1.20.1: MobEffects fields are raw MobEffect; wrap for the Holder-taking record.
                        new DeviceCapability.StatusEffectGrant(
                                BuiltInRegistries.MOB_EFFECT.wrapAsHolder(MobEffects.NIGHT_VISION), 0),
                        new DeviceCapability.PassiveDrain(ArmorOverloadRules.NIGHT_VISION_PASSIVE_DRAIN_FE)));
    }
}
