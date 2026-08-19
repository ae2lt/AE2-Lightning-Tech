package com.moakiee.ae2lt.item;

import java.util.List;

import com.moakiee.ae2lt.celestweave.ArmorPart;
import com.moakiee.ae2lt.celestweave.module.MultidimensionalProtectionSubmodule;
import com.moakiee.ae2lt.device.capability.DeviceCapability;

/**
 * Provides both complete incoming-damage cancellation and the existing last-stand fallback.
 * Neither capability declares passive, active FE, or Lightning costs.
 */
public final class MultidimensionalProtectionSubmoduleItem extends AbstractSingleArmorSubmoduleItem {
    public MultidimensionalProtectionSubmoduleItem(Properties properties) {
        super(
                properties,
                ArmorPart.CHEST,
                MultidimensionalProtectionSubmodule.INSTANCE,
                stack -> List.of(
                        new DeviceCapability.StagedMitigation(MultidimensionalProtectionSubmodule.ID),
                        new DeviceCapability.LastStandTuning(0L, 0)));
    }
}
