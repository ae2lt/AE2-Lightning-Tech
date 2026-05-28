package com.moakiee.ae2lt.item;

import java.util.List;

import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.overload.armor.ArmorOverloadRules;
import com.moakiee.ae2lt.overload.armor.ArmorPart;
import com.moakiee.ae2lt.overload.armor.module.ResistanceSubmodule;

public final class ResistanceSubmoduleItem extends AbstractSingleArmorSubmoduleItem {

    public ResistanceSubmoduleItem(
            Properties properties,
            ResistanceSubmodule submodule,
            String tooltipKey) {
        super(
                properties.stacksTo(1),
                ArmorPart.CHEST,
                submodule,
                tooltipKey,
                stack -> List.of(
                        new DeviceCapability.StagedMitigation(submodule.id()),
                        new DeviceCapability.PassiveDrain(ArmorOverloadRules.RESISTANCE_PASSIVE_DRAIN_FE)));
    }
}
