package com.moakiee.ae2lt.item;

import java.util.List;

import com.moakiee.ae2lt.celestweave.ArmorOverloadRules;
import com.moakiee.ae2lt.celestweave.ArmorPart;
import com.moakiee.ae2lt.celestweave.module.MovementAssistSubmodule;
import com.moakiee.ae2lt.device.capability.DeviceCapability;

public final class MovementAssistSubmoduleItem extends AbstractSingleArmorSubmoduleItem {
    public MovementAssistSubmoduleItem(Properties properties) {
        super(
                properties,
                ArmorPart.FEET,
                MovementAssistSubmodule.INSTANCE,
                stack -> List.of(
                        new DeviceCapability.MovementAssist(),
                        new DeviceCapability.PassiveDrain(ArmorOverloadRules.MOVEMENT_ASSIST_PASSIVE_DRAIN_FE)));
    }
}
