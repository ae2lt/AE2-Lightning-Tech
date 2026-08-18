package com.moakiee.ae2lt.logic.craft;

import java.util.List;

public interface MatrixCraftCore {
    default int threadCapacity() {
        return 0;
    }

    default List<MatrixCraftingUnit> craftingUnits() {
        int capacity = threadCapacity();
        return capacity > 0 ? List.of(MatrixCraftingUnit.threadPower(capacity)) : List.of();
    }
}
