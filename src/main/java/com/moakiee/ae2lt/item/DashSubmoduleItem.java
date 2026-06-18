package com.moakiee.ae2lt.item;

import java.util.List;

import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.celestweave.ArmorOverloadRules;
import com.moakiee.ae2lt.celestweave.ArmorPart;
import com.moakiee.ae2lt.celestweave.module.DashSubmodule;

public final class DashSubmoduleItem extends AbstractSingleArmorSubmoduleItem {

    public DashSubmoduleItem(Properties properties) {
        super(
                properties,
                ArmorPart.FEET,
                DashSubmodule.INSTANCE,
                stack -> List.of(
                        new DeviceCapability.DashEffect(1.8D, 40),
                        new DeviceCapability.PassiveDrain(ArmorOverloadRules.DASH_PASSIVE_DRAIN_FE),
                        new DeviceCapability.ActiveCost("dash", ArmorOverloadRules.DASH_ACTIVE_COST_FE)));
    }
}
