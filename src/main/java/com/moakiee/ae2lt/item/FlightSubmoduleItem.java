package com.moakiee.ae2lt.item;

import java.util.List;

import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.device.capability.FlightKind;
import com.moakiee.ae2lt.overload.armor.ArmorOverloadRules;
import com.moakiee.ae2lt.overload.armor.ArmorPart;
import com.moakiee.ae2lt.overload.armor.module.FlightSubmodule;

public final class FlightSubmoduleItem extends AbstractSingleArmorSubmoduleItem {

    public FlightSubmoduleItem(Properties properties) {
        super(
                properties.stacksTo(1),
                ArmorPart.LEGS,
                FlightSubmodule.INSTANCE,
                "item.ae2lt.module_creative_flight.tooltip",
                stack -> List.of(
                        new DeviceCapability.FlightMode(FlightKind.CREATIVE),
                        new DeviceCapability.PassiveDrain(ArmorOverloadRules.FLIGHT_HOVER_DRAIN_FE)));
    }
}
