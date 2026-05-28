package com.moakiee.ae2lt.item;

import java.util.List;

import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.overload.armor.ArmorOverloadRules;
import com.moakiee.ae2lt.overload.armor.ArmorPart;
import com.moakiee.ae2lt.overload.armor.module.CleanseSubmodule;

public final class CleanseSubmoduleItem extends AbstractSingleArmorSubmoduleItem {

    public CleanseSubmoduleItem(Properties properties) {
        super(
                properties.stacksTo(1),
                ArmorPart.CHEST,
                CleanseSubmodule.INSTANCE,
                "item.ae2lt.module_cleanse.tooltip",
                stack -> List.of(
                        new DeviceCapability.CleanseTuning(AE2LTCommonConfig.overloadArmorCleansePeriodTicks(), 1),
                        new DeviceCapability.PassiveDrain(ArmorOverloadRules.CLEANSE_PASSIVE_DRAIN_FE)));
    }
}
