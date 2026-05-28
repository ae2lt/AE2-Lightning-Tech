package com.moakiee.ae2lt.item;

import java.util.List;

import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.overload.armor.ArmorOverloadRules;
import com.moakiee.ae2lt.overload.armor.ArmorPart;
import com.moakiee.ae2lt.overload.armor.module.DigAffinitySubmodule;

public final class DigAffinitySubmoduleItem extends AbstractSingleArmorSubmoduleItem {

    public DigAffinitySubmoduleItem(Properties properties) {
        super(
                properties.stacksTo(1),
                ArmorPart.FEET,
                DigAffinitySubmodule.INSTANCE,
                "item.ae2lt.module_dig_affinity.tooltip",
                stack -> List.of(
                        new DeviceCapability.DigAffinity("underwater", AE2LTCommonConfig.overloadArmorUnderwaterDigMultiplier()),
                        new DeviceCapability.DigAffinity("airborne", AE2LTCommonConfig.overloadArmorAirborneDigMultiplier()),
                        new DeviceCapability.PassiveDrain(ArmorOverloadRules.DIG_AFFINITY_PASSIVE_DRAIN_FE)));
    }
}
