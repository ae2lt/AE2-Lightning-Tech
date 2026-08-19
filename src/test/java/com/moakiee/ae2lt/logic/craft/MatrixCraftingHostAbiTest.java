package com.moakiee.ae2lt.logic.craft;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.moakiee.ae2lt.blockentity.MatrixControllerBlockEntity;
import com.moakiee.ae2lt.crafting.matrix.core.CraftingCoreHost;

class MatrixCraftingHostAbiTest {
    @Test
    void controllerUsesTheLocalCraftingCoreHostContract() {
        var method = assertDoesNotThrow(
                () -> MatrixControllerBlockEntity.class.getMethod("isRemoved"));

        assertEquals(boolean.class, method.getReturnType());
        assertTrue(CraftingCoreHost.class.isAssignableFrom(MatrixControllerBlockEntity.class));
    }
}
