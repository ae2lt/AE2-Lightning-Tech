package com.moakiee.ae2lt.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class LargeStackInteractionContractTest {
    private static final Path MENU_DIR = Path.of("src/main/java/com/moakiee/ae2lt/menu");

    @Test
    void sharedPolicyRejectsUnsafeDirectExtractionAndCapsCursorStacks() throws Exception {
        String source = Files.readString(MENU_DIR.resolve("LargeStackAppEngSlot.java"));

        assertTrue(source.contains(
                "return clickType == ClickType.SWAP;"));
        assertTrue(source.contains("int nativeMax = slotStack.getMaxStackSize();"),
                "Normal pickup must respect the item's native stack size");
        assertTrue(source.contains("slotStack.getCount() > slotStack.getMaxStackSize()"),
                "Cursor swaps must reject oversized machine stacks");
        assertTrue(source.contains("actual.copyWithCount(actual.getMaxStackSize())"),
                "The menu slot must expose a native-sized presentation proxy");
        assertTrue(source.contains("Math.min(amount, actual.getMaxStackSize())"),
                "Generic slot extraction must return at most one native stack");
        assertTrue(source.contains("backingInventory.insertItem(backingSlot, offered, false)"),
                "Insertion must mutate the backing inventory instead of the presentation proxy");
        assertTrue(source.contains("setNotDraggable();"),
                "Quick-craft dragging must not replace an oversized backing stack with a presentation copy");
        assertTrue(source.contains("var slotStack = slot.getBackingItem();"),
                "The authoritative click handler must operate on the backing stack");
    }

    @Test
    void everyLargeStackMachineMenuUsesTheSharedGuardBeforeVanilla() throws Exception {
        Set<String> menuNames;
        try (var files = Files.list(MENU_DIR)) {
            menuNames = files
                    .filter(path -> path.getFileName().toString().endsWith("Menu.java"))
                    .filter(LargeStackInteractionContractTest::constructsLargeStackSlot)
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toSet());
        }

        assertEquals(Set.of(
                "AtmosphericIonizerMenu.java",
                "CrystalCatalyzerMenu.java",
                "LightningAssemblyChamberMenu.java",
                "LightningCollectorMenu.java",
                "LightningSimulationChamberMenu.java",
                "OverloadProcessingFactoryMenu.java",
                "TeslaCoilMenu.java"), menuNames);

        for (String menuName : menuNames) {
            assertServerMenuGuard(menuName);
        }
    }

    private static boolean constructsLargeStackSlot(Path path) {
        try {
            return Files.readString(path).contains("new LargeStackAppEngSlot");
        } catch (Exception e) {
            throw new IllegalStateException("Could not inspect " + path, e);
        }
    }

    private static void assertServerMenuGuard(String fileName) throws Exception {
        String source = Files.readString(MENU_DIR.resolve(fileName)).replace("\r\n", "\n");
        int methodStart = source.indexOf("public void clicked(int slotId, int button, ClickType clickType, Player player)");
        int methodEnd = source.indexOf("\n    @Override", methodStart + 1);

        assertTrue(methodStart >= 0, "Missing clicked override in " + fileName);
        assertTrue(methodEnd > methodStart, "Could not isolate clicked override in " + fileName);

        String method = source.substring(methodStart, methodEnd);
        int guard = method.indexOf("LargeStackAppEngSlot.handleMenuInteraction(this, slotId, button, clickType, player)");
        int delegate = method.indexOf("super.clicked(slotId, button, clickType, player)");

        assertTrue(guard >= 0, "Missing shared large-stack interaction guard in " + fileName);
        assertTrue(delegate > guard, "Server-side guard must run before vanilla delegation in " + fileName);

        int destinationsStart = source.indexOf("private List<Slot> getPlayerDestinationSlots()");
        int destinationsEnd = source.indexOf("private static ItemStack moveIntoSlots", destinationsStart);
        assertTrue(destinationsStart >= 0 && destinationsEnd > destinationsStart,
                "Could not isolate player destination whitelist in " + fileName);
        String destinations = source.substring(destinationsStart, destinationsEnd);
        assertTrue(destinations.contains("SlotSemantics.PLAYER_INVENTORY"),
                "Quick move must include the player inventory in " + fileName);
        assertTrue(destinations.contains("SlotSemantics.PLAYER_HOTBAR"),
                "Quick move must include the player hotbar in " + fileName);
    }
}
