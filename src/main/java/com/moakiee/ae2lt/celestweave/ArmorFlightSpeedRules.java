package com.moakiee.ae2lt.celestweave;

import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.celestweave.module.FlightSpeedOption;
import com.moakiee.ae2lt.celestweave.module.FlightSubmodule;
import com.moakiee.ae2lt.celestweave.module.PhaseFlightSubmodule;

public final class ArmorFlightSpeedRules {
    private ArmorFlightSpeedRules() {
    }

    public static float activeFlightSpeed(ItemStack armor) {
        if (CelestweaveArmorState.isSubmoduleRuntimeActive(armor, PhaseFlightSubmodule.INSTANCE.id())) {
            return (float) Math.max(FlightSpeedOption.VANILLA_FLYING_SPEED, PhaseFlightSubmodule.phaseSpeed(armor));
        }
        if (CelestweaveArmorState.isSubmoduleRuntimeActive(armor, FlightSubmodule.INSTANCE.id())) {
            return FlightSubmodule.flightSpeed(armor);
        }
        return FlightSpeedOption.VANILLA_FLYING_SPEED;
    }
}
