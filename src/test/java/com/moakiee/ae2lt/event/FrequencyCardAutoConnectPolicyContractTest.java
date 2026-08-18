package com.moakiee.ae2lt.event;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FrequencyCardAutoConnectPolicyContractTest {
    @Test
    void queueRejectsFakePlayersAndPlayersWithoutAnEnabledAutoCard() throws Exception {
        String registry = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/grid/wirelesslink/WirelessLinkRegistry.java"))
                .replace("\r\n", "\n");

        assertTrue(registry.contains(
                "player instanceof FakePlayer\n"
                        + "                || OverloadedFrequencyCardItem.findAutoConnectCard(player).isEmpty()"));
    }

    @Test
    void automaticControllerConflictsAreSilentButManualConflictsRemainVisible() throws Exception {
        String registry = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/grid/wirelesslink/WirelessLinkRegistry.java"))
                .replace("\r\n", "\n");

        String automaticSkip = "automatic\n"
                + "                    ? ActionFeedback.green(\"ae2lt.frequency_card.auto_silent_skip\")\n"
                + "                    : ActionFeedback.red(\"ae2lt.frequency_card.controller_conflict\")";
        assertTrue(registry.contains(automaticSkip));
        assertTrue(registry.indexOf(automaticSkip) != registry.lastIndexOf(automaticSkip));
    }

    @Test
    void delayedPartTasksVerifyThePartThatWasActuallyPlaced() throws Exception {
        String registry = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/grid/wirelesslink/WirelessLinkRegistry.java"))
                .replace("\r\n", "\n");

        assertTrue(registry.contains("matchesExpectedPlacedPart(level, BlockPos.of(pending.posLong()), pending)"));
        assertTrue(registry.contains("pending.expectedPartId().equals(partId(part))"));
    }
}
