package com.moakiee.ae2lt.logic;

public final class PassiveAeCharger {

    private PassiveAeCharger() {
    }

    public static boolean charge(Storage storage, double passiveAePerTick) {
        if (!(passiveAePerTick > 0.0D)) {
            return false;
        }

        double current = storage.getAECurrentPower();
        double max = storage.getAEMaxPower();
        double remaining = max - current;
        if (!(remaining > 0.0D)) {
            return false;
        }

        storage.setInternalCurrentPower(current + Math.min(passiveAePerTick, remaining));
        return true;
    }

    public interface Storage {
        double getAECurrentPower();

        double getAEMaxPower();

        void setInternalCurrentPower(double amount);
    }
}
