package com.moakiee.ae2lt.logic.compute;

public record ComputingUnitTotals(
        int dispatchUnits,
        int amplifierUnits,
        int storageUnits,
        int coolingUnits) {
    public ComputingUnitTotals {
        if (dispatchUnits < 0 || amplifierUnits < 0 || storageUnits < 0 || coolingUnits < 0) {
            throw new IllegalArgumentException("Computing unit totals must not be negative");
        }
    }
}
