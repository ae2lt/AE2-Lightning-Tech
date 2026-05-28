package com.moakiee.ae2lt.item;

import java.util.List;

import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.overload.armor.ArmorOverloadRules;
import com.moakiee.ae2lt.overload.armor.ArmorPart;
import com.moakiee.ae2lt.overload.armor.module.UndyingSubmodule;

public final class UndyingSubmoduleItem extends AbstractSingleArmorSubmoduleItem {

    public UndyingSubmoduleItem(Properties properties) {
        super(
                properties.stacksTo(1),
                ArmorPart.CHEST,
                UndyingSubmodule.INSTANCE,
                "item.ae2lt.module_undying.tooltip",
                stack -> List.of(
                        new DeviceCapability.LastStandTuning(
                                ArmorOverloadRules.UNDYING_TRIGGER_COST_FE,
                                ArmorOverloadRules.UNDYING_COOLDOWN_TICKS,
                                ArmorOverloadRules.UNDYING_COMBO_WINDOW_TICKS),
                        new DeviceCapability.PassiveDrain(ArmorOverloadRules.UNDYING_PASSIVE_DRAIN_FE)));
    }
}
