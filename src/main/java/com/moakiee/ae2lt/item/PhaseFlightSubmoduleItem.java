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
                        // Phase flight is an upgrade of creative flight, not a second flight mode.
                        // The traversal capability is charged only while its no-clip feature is in use.
                        new DeviceCapability.FlightMode(FlightKind.CREATIVE),
                        new DeviceCapability.PassiveDrain(ArmorOverloadRules.FLIGHT_HOVER_DRAIN_FE),
                        new DeviceCapability.PhaseTraversal(ArmorOverloadRules.PHASE_FLIGHT_PASSIVE_DRAIN_FE)));
    }
}
