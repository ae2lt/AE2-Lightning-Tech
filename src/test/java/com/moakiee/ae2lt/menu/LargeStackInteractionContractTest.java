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

        assertTrue(source.contains("return clickType == ClickType.SWAP;"));
        assertTrue(source.contains("int nativeMax = slotStack.getMaxStackSize();"),
                "Normal pickup must respect the item's native stack size");
        assertTrue(source.contains("slotStack.getCount() > slotStack.getMaxStackSize()"),
                "Cursor swaps must reject oversized machine stacks");
        assertTrue(source.contains("GenericStack.wrapInItemStack(key, actual.getCount())"),
                "Oversized menu stacks must use AE2's long-count presentation wrapper");
        assertTrue(source.contains("if (isRemote() && hasSynchronizedDisplayStack)"),
                "Client display synchronization must not overwrite the real machine inventory");
        assertTrue(source.contains("if (menu.isClientSide())"),
                "Only the authoritative server may mutate the backing inventory");
        assertTrue(source.contains("Math.min(amount, actual.getMaxStackSize())"),
                "Generic slot extraction must return at most one native stack");
        assertTrue(source.contains("backingInventory.insertItem(backingSlot, offered, false)"),
                "Insertion must mutate the backing inventory instead of the presentation proxy");
        assertTrue(source.contains("backingInventory.isItemValid(backingSlot, stack)"),
                "Insertion validation must consult the backing inventory even while the display is wrapped");
        assertTrue(source.contains("setNotDraggable();"),
                "Quick-craft dragging must not replace an oversized backing stack with a presentation copy");
        assertTrue(source.contains("var slotStack = slot.getBackingItem();"),
                "The authoritative click handler must operate on the backing stack");
        assertTrue(source.contains("case QUICK_MOVE -> handleQuickMove"),
                "Shift-click extraction must bypass the read-only presentation wrapper safely");
        assertTrue(source.contains("case THROW -> handleThrow"),
                "Q and Ctrl+Q must materialize only native-sized stacks");
    }

    @Test
    void commonAe2MenuMixinOwnsTheGuardAndMachinesDoNotRepeatIt() throws Exception {
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

        String mixin = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/mixin/AEBaseMenuManagedSlotClickMixin.java"));
        assertTrue(mixin.contains("@Mixin(AEBaseMenu.class)"));
        assertTrue(mixin.contains("@Inject(method = \"clicked\", at = @At(\"HEAD\"), cancellable = true)"));
        assertTrue(mixin.contains("LargeStackAppEngSlot.handleMenuInteraction(menu, slotId, button, clickType, player)"));
        assertTrue(mixin.contains("button != 40"),
                "The overloaded provider return slot must reject the offhand SWAP button");
        assertTrue(mixin.contains("SlotSemantics.STORAGE"),
                "The provider rule must be limited to return/storage slots");

        String mixinConfig = Files.readString(Path.of("src/main/resources/ae2lt.mixins.json"));
        assertTrue(mixinConfig.contains("\"AEBaseMenuManagedSlotClickMixin\""));

        for (String menuName : menuNames) {
            assertNoRepeatedMenuGuard(menuName);
        }
    }

    private static boolean constructsLargeStackSlot(Path path) {
        try {
            return Files.readString(path).contains("new LargeStackAppEngSlot");
        } catch (Exception e) {
            throw new IllegalStateException("Could not inspect " + path, e);
        }
    }

    private static void assertNoRepeatedMenuGuard(String fileName) throws Exception {
        String source = Files.readString(MENU_DIR.resolve(fileName)).replace("\r\n", "\n");
        assertTrue(!source.contains("LargeStackAppEngSlot.handleMenuInteraction("),
                "Large-stack click forwarding must stay centralized, not repeated in " + fileName);

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
