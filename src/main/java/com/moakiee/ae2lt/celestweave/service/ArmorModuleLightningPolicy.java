package com.moakiee.ae2lt.celestweave.service;

import java.util.List;

import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.celestweave.ArmorOverloadRules;
import com.moakiee.ae2lt.celestweave.service.ArmorLightningService.LightningCost;

public final class ArmorModuleLightningPolicy {
    public enum Trigger {
        DASH,
        MATRIX_SHIELD,
        PHASE_SHIELD,
        REFLECT,
        UNDYING,
        PURIFICATION,
        SATURATION,
        DIG_AFFINITY
    }

    private ArmorModuleLightningPolicy() {
    }

    public static LightningCost passiveCost(
            List<DeviceCapability> capabilities,
            boolean movingFlight,
            boolean phaseTraversalActive,
            long reachHvPerTick,
            long flightHvPerTick,
            long phaseFlightHvPerTick) {
        boolean creativeFlight = false;
        boolean phaseTraversal = false;
        boolean reachExtension = false;
        for (DeviceCapability capability : capabilities) {
            if (capability instanceof DeviceCapability.FlightMode mode) {
                creativeFlight = true;
            } else if (capability instanceof DeviceCapability.PhaseTraversal) {
                phaseTraversal = true;
            } else if (capability instanceof DeviceCapability.InteractionRange) {
                reachExtension = true;
            }
        }

        if (phaseTraversal && phaseTraversalActive) {
            return LightningCost.hv(phaseFlightHvPerTick);
        }
        if (creativeFlight) {
            long amount = flightHvPerTick;
            if (movingFlight) {
                amount += flightHvPerTick;
            }
            return LightningCost.hv(amount);
        }
        if (reachExtension) {
            return LightningCost.hv(reachHvPerTick);
        }
        return LightningCost.NONE;
    }

    public static LightningCost triggeredCost(Trigger trigger) {
        return switch (trigger) {
            case MATRIX_SHIELD -> LightningCost.hv(1L);
            case PHASE_SHIELD -> LightningCost.ehv(1L);
            case UNDYING -> LightningCost.ehv(ArmorOverloadRules.UNDYING_TRIGGER_COST_EHV);
            default -> LightningCost.NONE;
        };
    }
}
