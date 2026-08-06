package com.moakiee.ae2lt.client.railgun;

enum RailgunChargeSoundPhase {
    RAMP,
    SUSTAIN;

    static RailgunChargeSoundPhase fromChargeTicks(long chargeTicks, long fullChargeTicks) {
        return chargeTicks >= fullChargeTicks ? SUSTAIN : RAMP;
    }
}
