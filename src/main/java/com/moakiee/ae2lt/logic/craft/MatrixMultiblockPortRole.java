package com.moakiee.ae2lt.logic.craft;

public enum MatrixMultiblockPortRole {
    CONTROLLER(false, true),
    INTERFACE(true, true),
    STRUCTURE(false, false);

    private final boolean exposesAeNetwork;
    private final boolean requiredForFormation;

    MatrixMultiblockPortRole(boolean exposesAeNetwork, boolean requiredForFormation) {
        this.exposesAeNetwork = exposesAeNetwork;
        this.requiredForFormation = requiredForFormation;
    }

    public boolean exposesAeNetwork() {
        return exposesAeNetwork;
    }

    public boolean requiredForFormation() {
        return requiredForFormation;
    }
}
