package com.moakiee.ae2lt.item;

import java.util.List;

import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.celestweave.ArmorOverloadRules;
import com.moakiee.ae2lt.celestweave.ArmorPart;
import com.moakiee.ae2lt.celestweave.module.SaturationSubmodule;

public final class SaturationSubmoduleItem extends AbstractSingleArmorSubmoduleItem {

    public SaturationSubmoduleItem(Properties properties) {
        super(
                properties,
                ArmorPart.HEAD,
                SaturationSubmodule.INSTANCE,
                stack -> List.of(
                        new DeviceCapability.FoodSustain(
                                20,
                                20.0F,
                                AE2LTCommonConfig.overloadArmorSaturationCheckIntervalTicks()),
                        new DeviceCapability.PassiveDrain(ArmorOverloadRules.SATURATION_PASSIVE_DRAIN_FE)));
    }
}
