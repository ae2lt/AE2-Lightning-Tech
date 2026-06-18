package com.moakiee.ae2lt.item;

import java.util.List;

import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.device.capability.FlightKind;
import com.moakiee.ae2lt.celestweave.ArmorOverloadRules;
import com.moakiee.ae2lt.celestweave.ArmorPart;
import com.moakiee.ae2lt.celestweave.module.PhaseFlightSubmodule;

public final class PhaseFlightSubmoduleItem extends AbstractSingleArmorSubmoduleItem {

    public PhaseFlightSubmoduleItem(Properties properties) {
        super(
                properties,
                ArmorPart.LEGS,
                PhaseFlightSubmodule.INSTANCE,
                stack -> List.of(
                        new DeviceCapability.FlightMode(FlightKind.PHASE),
                        new DeviceCapability.PassiveDrain(ArmorOverloadRules.PHASE_FLIGHT_PASSIVE_DRAIN_FE)));
    }
}
