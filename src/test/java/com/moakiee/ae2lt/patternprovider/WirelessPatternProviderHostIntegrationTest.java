package com.moakiee.ae2lt.patternprovider;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class WirelessPatternProviderHostIntegrationTest {
    @Test
    void connectorSelectionEditingAndRenderingUseTheStableHostContract()
            throws IOException {
        var blockEntity = read(
                "src/main/java/com/moakiee/ae2lt/blockentity/"
                        + "OverloadedPatternProviderBlockEntity.java");
        var item = read(
                "src/main/java/com/moakiee/ae2lt/item/"
                        + "OverloadedWirelessConnectorItem.java");
        var packet = read(
                "src/main/java/com/moakiee/ae2lt/network/"
                        + "WirelessConnectorUsePacket.java");
        var renderer = read(
                "src/main/java/com/moakiee/ae2lt/client/"
                        + "WirelessConnectorRenderer.java");
        var mod = read(
                "src/main/java/com/moakiee/ae2lt/AE2LightningTech.java");

        assertTrue(blockEntity.contains(
                "implements FrequencyBindingHost, WirelessPatternProviderHost"));
        assertTrue(item.contains(
                "targetBe instanceof WirelessPatternProviderHost"));
        assertTrue(packet.contains(
                "provider.addOrUpdateConnection("));
        assertTrue(packet.contains(
                "provider.removeConnection("));
        assertTrue(renderer.contains(
                "be instanceof WirelessPatternProviderHost provider"));
        assertTrue(mod.contains(
                "WirelessPatternProviderPolicy.setMaxDistanceSupplier("));
    }

    @Test
    void publicFrequencyAccessCarriesLegacyMemoryCardSettings()
            throws IOException {
        var access = read(
                "src/main/java/com/moakiee/ae2lt/api/frequency/"
                        + "FrequencyBindingAccess.java");
        var helper = read(
                "src/main/java/com/moakiee/ae2lt/grid/"
                        + "FrequencyBindingHelper.java");

        assertTrue(access.contains("default void exportMemorySettings("));
        assertTrue(access.contains("default void importMemorySettings("));
        assertTrue(helper.contains(
                "MemoryCardConfigSupport.exportMemoryCardSettings("));
        assertTrue(helper.contains(
                "MemoryCardConfigSupport.importMemoryCardSettings("));
        assertTrue(helper.contains("writeMemoryFrequency(tag, frequencyId)"));
        assertTrue(helper.contains(
                "importMemoryFrequency(tag, this::setFrequency)"));
    }

    @Test
    void overloadPatternExposesPayloadValidationWithoutAddonTypeChecks()
            throws IOException {
        var item = read(
                "src/main/java/com/moakiee/ae2lt/item/OverloadPatternItem.java");

        assertTrue(item.contains(
                "implements EncodedPatternPayloadValidator"));
        assertTrue(item.contains(
                "boolean hasEncodedPatternPayload(ItemStack stack)"));
        assertTrue(item.contains("return hasPayload(stack)"));
    }

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path));
    }
}
