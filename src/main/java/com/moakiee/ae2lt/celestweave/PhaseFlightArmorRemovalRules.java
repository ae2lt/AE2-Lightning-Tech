package com.moakiee.ae2lt.celestweave;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

final class PhaseFlightArmorRemovalRules {
    private PhaseFlightArmorRemovalRules() {
    }

    static boolean shouldDeactivateRemovedArmor(@Nullable UUID removedArmorId, @Nullable UUID replacementArmorId) {
        return removedArmorId != null && !removedArmorId.equals(replacementArmorId);
    }
}
