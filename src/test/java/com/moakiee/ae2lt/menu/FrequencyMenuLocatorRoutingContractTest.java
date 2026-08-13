package com.moakiee.ae2lt.menu;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class FrequencyMenuLocatorRoutingContractTest {
    @Test
    void choosesLocatorHostTypeFromParentMenuInsteadOfProbing() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/menu/FrequencyMenu.java"));

        assertTrue(source.contains(
                "if (!(serverPlayer.containerMenu instanceof FrequencyBindingMenuHost))"));
        assertTrue(source.contains(
                "locator.locate(serverPlayer, ItemMenuHost.class)"));
        assertTrue(source.contains(
                "locator.locate(serverPlayer, FrequencyBindingHost.class)"));
    }
}
