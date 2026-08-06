package com.moakiee.ae2lt.item;

import java.util.List;

import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.celestweave.ArmorOverloadRules;
import com.moakiee.ae2lt.celestweave.ArmorPart;
import com.moakiee.ae2lt.celestweave.module.ReachSubmodule;

public final class ReachSubmoduleItem extends AbstractSingleArmorSubmoduleItem {

    public ReachSubmoduleItem(Properties properties) {
        super(
                properties,
                ArmorPart.CHEST,
                ReachSubmodule.INSTANCE,
                stack -> List.of(
                        new DeviceCapability.InteractionRange(),
                        new DeviceCapability.PassiveDrain(ArmorOverloadRules.REACH_EXTENSION_PASSIVE_DRAIN_FE)));
    }
}
