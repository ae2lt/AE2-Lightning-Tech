package com.moakiee.ae2lt.grid.wirelesslink;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FrequencyDeletionCascadeContractTest {
    private static final Path MAIN_JAVA = Path.of("src/main/java/com/moakiee/ae2lt");

    @Test
    void frequencyIsInvalidatedBeforeLinksAndControllerAreCleared() throws Exception {
        String source = Files.readString(MAIN_JAVA.resolve("grid/WirelessFrequencyManager.java"));

        assertAppearsBefore(source, "frequencies.remove(id)", "linkRegistry.removeFrequencyLinks(id)");
        assertAppearsBefore(source, "linkRegistry.removeFrequencyLinks(id)", "ctrl.clearFrequency()");
    }

    @Test
    void linkPurgeCancelsDelayedInheritanceBeforeRemovingRuntimeLinks() throws Exception {
        String source = Files.readString(
                MAIN_JAVA.resolve("grid/wirelesslink/WirelessLinkRegistry.java"));
        int methodStart = source.indexOf("public int removeFrequencyLinks(int frequencyId)");
        int nextMethod = source.indexOf("private void discardPendingFrequencyInheritance", methodStart);
        assertTrue(methodStart >= 0 && nextMethod > methodStart);
        String purgeMethod = source.substring(methodStart, nextMethod);

        assertTrue(purgeMethod.contains("links.findAllForFrequency(frequencyId)"));
        assertAppearsBefore(
                purgeMethod,
                "discardPendingFrequencyInheritance(frequencyId, removedLinkIds)",
                "removeLinks(frequencyLinks)");
        assertTrue(source.contains("manager.isFrequencyValid(inheritance.frequencyId())"));
    }

    private static void assertAppearsBefore(String source, String first, String second) {
        int firstIndex = source.indexOf(first);
        int secondIndex = source.indexOf(second);
        assertTrue(firstIndex >= 0, () -> "Missing source fragment: " + first);
        assertTrue(secondIndex >= 0, () -> "Missing source fragment: " + second);
        assertTrue(firstIndex < secondIndex, () -> first + " must appear before " + second);
    }
}
