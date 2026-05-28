package com.moakiee.ae2lt.item;

import java.util.List;

import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.device.capability.FlightKind;
import com.moakiee.ae2lt.overload.armor.ArmorOverloadRules;
import com.moakiee.ae2lt.overload.armor.ArmorPart;
import com.moakiee.ae2lt.overload.armor.module.PhaseFlightSubmodule;

public final class PhaseFlightSubmoduleItem extends AbstractSingleArmorSubmoduleItem {

    public PhaseFlightSubmoduleItem(Properties properties) {
        super(
                properties.stacksTo(1),
                ArmorPart.LEGS,
                PhaseFlightSubmodule.INSTANCE,
                "item.ae2lt.module_phase_flight.tooltip",
                stack -> List.of(
                        new DeviceCapability.FlightMode(FlightKind.PHASE),
                        new DeviceCapability.PassiveDrain(ArmorOverloadRules.PHASE_FLIGHT_PASSIVE_DRAIN_FE)));
    }
}
