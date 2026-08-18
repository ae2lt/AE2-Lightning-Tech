package com.moakiee.ae2lt.logic.craft;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.moakiee.ae2lt.blockentity.MatrixControllerBlockEntity;
import com.moakiee.thunderbolt.core.craft.CraftingCoreHost;

class MatrixCraftingHostAbiTest {
    @Test
    void controllerDeclaresTheCrossJarRemovalBridgeExplicitly() {
        var method = assertDoesNotThrow(
                () -> MatrixControllerBlockEntity.class.getDeclaredMethod(
                        "isCraftingHostRemoved"));

        assertEquals(boolean.class, method.getReturnType());
        assertTrue(CraftingCoreHost.class.isAssignableFrom(method.getDeclaringClass()));
    }
}
