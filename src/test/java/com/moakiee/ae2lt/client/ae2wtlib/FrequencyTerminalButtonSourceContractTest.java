package com.moakiee.ae2lt.client.ae2wtlib;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class FrequencyTerminalButtonSourceContractTest {
    @Test
    void toolbarMixinClassMatchesTheOptionalMixinConfigPackage() throws Exception {
        Path mixinSource = Path.of(
                "src/main/java/com/moakiee/ae2lt/mixin/ae2wtlib/client/"
                        + "AEBaseScreenFrequencyTerminalButtonMixin.java");
        String mixinConfig = Files.readString(Path.of("src/main/resources/ae2lt.ae2wtlib.mixins.json"));

        assertTrue(Files.exists(mixinSource));
        assertTrue(Files.readString(mixinSource).contains(
                "package com.moakiee.ae2lt.mixin.ae2wtlib.client;"));
        assertTrue(Files.readString(mixinSource).contains("@Mixin(AEBaseScreen.class)"));
        assertFalse(Files.readString(mixinSource).contains(
                "@Mixin(value = AEBaseScreen.class, remap = false)"));
        assertTrue(Files.readString(mixinSource).contains(
                "@Inject(method = \"updateBeforeRender\", at = @At(\"TAIL\"), require = 1, remap = false)"));
        assertTrue(mixinConfig.contains("\"package\": \"com.moakiee.ae2lt.mixin.ae2wtlib\""));
        assertTrue(mixinConfig.contains("\"client.AEBaseScreenFrequencyTerminalButtonMixin\""));
        assertFalse(Files.exists(Path.of(
                "src/main/java/com/moakiee/ae2lt/mixin/client/"
                        + "AEBaseScreenFrequencyTerminalButtonMixin.java")));
    }

    @Test
    void frequencyCardDetectionDoesNotDependOnVisibleScrollingSlots() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/ae2wtlib/FrequencyTerminalButton.java"));

        assertTrue(source.contains("screen.getMenu().getTarget() instanceof ItemMenuHost terminalHost"));
        assertTrue(source.contains("TerminalCardAccess.findCard(terminalHost.getUpgrades())"));
        assertFalse(source.contains("getSlots(SlotSemantics.UPGRADE)"));
        assertFalse(source.contains("AppEngSlot"));
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
