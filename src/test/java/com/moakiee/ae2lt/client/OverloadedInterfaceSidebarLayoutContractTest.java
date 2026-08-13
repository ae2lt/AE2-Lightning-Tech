package com.moakiee.ae2lt.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class OverloadedInterfaceSidebarLayoutContractTest {
    @Test
    void filterComponentUsesItsOwnAlignedSidebarPanel() throws Exception {
        String screen = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/OverloadedInterfaceScreen.java"));
        String menu = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/menu/OverloadedInterfaceMenu.java"));
        String style = Files.readString(Path.of(
                "src/main/resources/assets/ae2/screens/overloaded_interface.json"));

        assertTrue(screen.contains("Ae2ltSlotSemantics.OVERLOADED_INTERFACE_FILTER"));
        assertTrue(menu.contains(
                "addSlot(filterSlot, Ae2ltSlotSemantics.OVERLOADED_INTERFACE_FILTER)"));
        assertFalse(menu.contains("addSlot(filterSlot, SlotSemantics.UPGRADE)"));
        assertTrue(style.contains("\"right\": -2"));
        assertTrue(style.contains("\"top\": 88"));
    }
}
