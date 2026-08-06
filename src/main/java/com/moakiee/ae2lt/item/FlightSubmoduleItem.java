package com.moakiee.ae2lt.item;

import java.util.List;

import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.device.capability.FlightKind;
import com.moakiee.ae2lt.celestweave.ArmorOverloadRules;
import com.moakiee.ae2lt.celestweave.ArmorPart;
import com.moakiee.ae2lt.celestweave.module.FlightSubmodule;

public final class FlightSubmoduleItem extends AbstractSingleArmorSubmoduleItem {

    public FlightSubmoduleItem(Properties properties) {
        super(
                properties,
                ArmorPart.LEGS,
                FlightSubmodule.INSTANCE,
                stack -> List.of(
                        new DeviceCapability.FlightMode(FlightKind.CREATIVE),
                        new DeviceCapability.PassiveDrain(ArmorOverloadRules.FLIGHT_HOVER_DRAIN_FE)));
    }
}
