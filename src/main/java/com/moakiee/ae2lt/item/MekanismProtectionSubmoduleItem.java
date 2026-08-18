package com.moakiee.ae2lt.item;

import java.util.List;

import com.moakiee.ae2lt.celestweave.ArmorPart;
import com.moakiee.ae2lt.celestweave.module.MekanismProtectionSubmodule;
import com.moakiee.ae2lt.device.capability.DeviceCapability;

public final class MekanismProtectionSubmoduleItem extends AbstractSingleArmorSubmoduleItem {

    public MekanismProtectionSubmoduleItem(
            Properties properties,
            MekanismProtectionSubmodule submodule) {
        super(
                properties,
                ArmorPart.CHEST,
                submodule,
                stack -> List.of(new DeviceCapability.DamageTypeImmunity(submodule.damageType())));
    }
}
