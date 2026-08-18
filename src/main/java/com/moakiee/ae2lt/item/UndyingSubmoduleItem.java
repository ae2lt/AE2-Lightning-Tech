package com.moakiee.ae2lt.item;

import java.util.List;

import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.celestweave.ArmorOverloadRules;
import com.moakiee.ae2lt.celestweave.ArmorPart;
import com.moakiee.ae2lt.celestweave.module.UndyingSubmodule;

public final class UndyingSubmoduleItem extends AbstractSingleArmorSubmoduleItem {

    public UndyingSubmoduleItem(Properties properties) {
        super(
                properties,
                ArmorPart.CHEST,
                UndyingSubmodule.INSTANCE,
                stack -> List.of(
                        new DeviceCapability.LastStandTuning(
                                ArmorOverloadRules.UNDYING_TRIGGER_COST_FE,
                                AE2LTCommonConfig.overloadArmorUndyingComboWindowTicks()),
                        new DeviceCapability.PassiveDrain(ArmorOverloadRules.UNDYING_PASSIVE_DRAIN_FE)));
    }
}
