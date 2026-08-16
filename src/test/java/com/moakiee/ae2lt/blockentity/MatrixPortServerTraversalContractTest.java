package com.moakiee.ae2lt.blockentity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MatrixPortServerTraversalContractTest {
    @Test
    void structureValidationIsSharedByAllReadersInTheSameTick() throws Exception {
        String controller = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/blockentity/MatrixControllerBlockEntity.java"));

        assertTrue(controller.contains("validateStructureCacheForCurrentTick()"));
        assertTrue(controller.contains(
                "lastStructureCacheValidationTick == currentTick"));
        assertTrue(controller.contains("structureCacheValidationRequired = true;"));

        String findStorages = methodBody(controller,
                "public List<MatrixPatternStorageBlockEntity> findPatternStorages()");
        String findUnits = methodBody(controller,
                "public List<MatrixCraftingUnit> findCraftingUnits()");
        assertTrue(findStorages.contains("validateStructureCacheForCurrentTick()"));
        assertTrue(findUnits.contains("validateStructureCacheForCurrentTick()"));
        assertFalse(findStorages.contains("validateStructureCache()"));
        assertFalse(findUnits.contains("validateStructureCache()"));
    }

    @Test
    void terminalSlotLookupUsesThePrecomputedFlatMapping() throws Exception {
        String port = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/blockentity/MatrixPortBlockEntity.java"));

        String slotLookup = methodBody(port,
                "private TerminalPatternSlot terminalPatternSlot(int slot)");
        assertTrue(slotLookup.contains("getTerminalPatternSlots()"));
        assertFalse(slotLookup.contains("getPatternStorages()"));
        assertFalse(slotLookup.contains("for ("));

        String mappingLookup = methodBody(port,
                "private List<TerminalPatternSlot> getTerminalPatternSlots()");
        assertTrue(mappingLookup.contains("var storages = getPatternStorages();"));
        assertTrue(mappingLookup.contains("if (terminalPatternSlotsDirty)"));

        String inventorySize = methodBody(port, "public int size()",
                port.indexOf("class MatrixTerminalPatternInventory"));
        assertTrue(inventorySize.contains("getTerminalPatternSlots().size()"));
        assertFalse(inventorySize.contains("getPatternStorages()"));

        String updateLink = methodBody(port,
                "private void updateLinkState(boolean bindingChanged)");
        assertTrue(updateLink.contains("invalidateTerminalPatternSlots();"));
    }

    @Test
    void automationExposesAtMostOneReadableAndOneWritableStorage() throws Exception {
        String port = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/blockentity/MatrixPortBlockEntity.java"));
        String storage = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/blockentity/MatrixPatternStorageBlockEntity.java"));

        String selection = methodBody(port,
                "private List<MatrixPatternStorageBlockEntity> selectExposedPatternStorages()");
        assertTrue(selection.contains("!storage.isEmpty()"));
        assertTrue(selection.contains("storage.hasFreeSlot()"));
        assertTrue(selection.contains("storage != readable"));
        assertTrue(selection.contains("List.of(readable, writable)"));

        String patternsChanged = methodBody(port, "public void patternsChanged()");
        assertTrue(patternsChanged.contains("invalidateExposedPatternStorage();"),
                "Every storage mutation must refresh the readable/writable exposure");
        String storageChanged = methodBody(storage, "private void setChangedAndUpdate()");
        String notifyPort = methodBody(storage, "private void notifyPortPatternsChanged()");
        assertTrue(storageChanged.contains("notifyPortPatternsChanged();"));
        assertTrue(notifyPort.contains("port.patternsChanged();"));

        int handlerStart = port.indexOf("class PortPatternItemHandler");
        String slotCount = methodBody(port, "public int getSlots()", handlerStart);
        assertTrue(slotCount.contains("getExposedPatternStorages()"));
        assertFalse(slotCount.contains("getTerminalPatternSlots()"),
                "Automation must not expose every matrix storage slot");
    }

    @Test
    void patternValidationRejectsOrdinaryItemsBeforeDecoding() throws Exception {
        String storage = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/blockentity/MatrixPatternStorageBlockEntity.java"));

        String validation = methodBody(storage,
                "public boolean isValidPatternStack(ItemStack stack)");
        int encodedPatternGuard = validation.indexOf(
                "!PatternDetailsHelper.isEncodedPattern(stack)");
        int decodePattern = validation.indexOf("PatternDetailsHelper.decodePattern(stack, level)");

        assertTrue(encodedPatternGuard >= 0,
                "Ordinary automation inputs need a cheap pattern-item guard");
        assertTrue(decodePattern > encodedPatternGuard,
                "Only encoded patterns should pay the full decoder and component-hash cost");
    }

    private static String methodBody(String source, String signature) {
        return methodBody(source, signature, 0);
    }

    private static String methodBody(String source, String signature, int fromIndex) {
        int start = source.indexOf(signature, fromIndex);
        assertTrue(start >= 0, "Missing method: " + signature);
        int bodyStart = source.indexOf('{', start);
        int depth = 0;
        for (int i = bodyStart; i < source.length(); i++) {
            char current = source.charAt(i);
            if (current == '{') {
                depth++;
            } else if (current == '}' && --depth == 0) {
                return source.substring(bodyStart, i + 1);
            }
        }
        throw new AssertionError("Unterminated method: " + signature);
    }
}
