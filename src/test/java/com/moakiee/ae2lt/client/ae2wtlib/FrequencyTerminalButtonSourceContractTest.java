package com.moakiee.ae2lt.client.ae2wtlib;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class FrequencyTerminalButtonSourceContractTest {
    @Test
    void frequencyCardDetectionDoesNotDependOnVisibleScrollingSlots() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/ae2wtlib/FrequencyTerminalButton.java"));

        assertTrue(source.contains("slot instanceof AppEngSlot appEngSlot"));
        assertTrue(source.contains("appEngSlot.getSlotInv().getStackInSlot(0)"));
        assertFalse(source.contains("var stack = slot.getItem()"));
    }

    @Test
    void autoConnectButtonDisplaysTheStateSynchronizedByTheMenu() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/ae2wtlib/FrequencyTerminalButton.java"));

        assertTrue(source.contains(
                "autoConnectButton.setState(OverloadedFrequencyCardItem.getData(card).autoConnect())"));
        assertFalse(source.contains("pendingAutoConnect"));
        assertFalse(source.contains("SYNC_GRACE_TICKS"));
    }

    @Test
    void serverToggleUpdatesTheUpgradeInventoryOwnedByTheOpenMenu() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/network/ToggleFrequencyCardAutoConnectPacket.java"));

        assertTrue(source.contains("aeMenu.getTarget() instanceof ItemMenuHost terminalHost"));
        assertTrue(source.contains("var upgrades = terminalHost.getUpgrades()"));
        assertTrue(source.contains("TerminalCardAccess.updateCard(upgrades"));
        assertFalse(source.contains("TerminalCardAccess.updateCard(terminal,"));
    }
}
