package com.moakiee.ae2lt.logic.craft;

import java.util.Objects;

import net.minecraft.core.Direction;

public record MatrixMultiblockPortLayout(Direction controllerFace, Direction interfaceFace) {
    public MatrixMultiblockPortLayout {
        Objects.requireNonNull(controllerFace);
        Objects.requireNonNull(interfaceFace);
    }

    public boolean usesPreferredSameFacePlacement() {
        return controllerFace == interfaceFace;
    }
}
