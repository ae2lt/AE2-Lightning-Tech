package com.moakiee.ae2lt.item;

import java.util.List;

import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.overload.armor.ArmorOverloadRules;
import com.moakiee.ae2lt.overload.armor.ArmorPart;
import com.moakiee.ae2lt.overload.armor.module.DashSubmodule;

public final class DashSubmoduleItem extends AbstractSingleArmorSubmoduleItem {

    public DashSubmoduleItem(Properties properties) {
        super(
                properties.stacksTo(64),
                ArmorPart.FEET,
                DashSubmodule.INSTANCE,
                "item.ae2lt.module_dash.tooltip",
                stack -> List.of(
                        new DeviceCapability.DashEffect(1.8D, 60),
                        new DeviceCapability.PassiveDrain(ArmorOverloadRules.DASH_PASSIVE_DRAIN_FE),
                        new DeviceCapability.ActiveCost("dash", ArmorOverloadRules.DASH_ACTIVE_COST_FE)));
    }
}
