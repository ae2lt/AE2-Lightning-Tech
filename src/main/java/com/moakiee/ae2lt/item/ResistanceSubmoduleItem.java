package com.moakiee.ae2lt.item;

import java.util.List;

import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.celestweave.ArmorOverloadRules;
import com.moakiee.ae2lt.celestweave.ArmorPart;
import com.moakiee.ae2lt.celestweave.module.ResistanceSubmodule;

public final class ResistanceSubmoduleItem extends AbstractSingleArmorSubmoduleItem {

    public ResistanceSubmoduleItem(
            Properties properties,
            ResistanceSubmodule submodule) {
        super(
                properties,
                ArmorPart.CHEST,
                submodule,
                stack -> List.of(
                        new DeviceCapability.StagedMitigation(submodule.id()),
                        new DeviceCapability.PassiveDrain(ArmorOverloadRules.RESISTANCE_PASSIVE_DRAIN_FE)));
    }
}
