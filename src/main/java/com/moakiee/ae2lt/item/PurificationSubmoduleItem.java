package com.moakiee.ae2lt.item;

import java.util.List;

import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.celestweave.ArmorOverloadRules;
import com.moakiee.ae2lt.celestweave.ArmorPart;
import com.moakiee.ae2lt.celestweave.module.PurificationSubmodule;

public final class PurificationSubmoduleItem extends AbstractSingleArmorSubmoduleItem {

    public PurificationSubmoduleItem(Properties properties) {
        super(
                properties,
                ArmorPart.CHEST,
                PurificationSubmodule.INSTANCE,
                stack -> List.of(
                        new DeviceCapability.PurificationTuning(
                                AE2LTCommonConfig.overloadArmorPurificationPeriodTicks(),
                                Integer.MAX_VALUE),
                        new DeviceCapability.PassiveDrain(ArmorOverloadRules.PURIFICATION_PASSIVE_DRAIN_FE)));
    }
}
