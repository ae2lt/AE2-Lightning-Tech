package com.moakiee.ae2lt.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class ResearchNoteBookOpeningSourceContractTest {
    @Test
    void generatedNotesUseTheServerSnapshotPacketInsteadOfTheVanillaBookPacket() throws Exception {
        String item = source("src/main/java/com/moakiee/ae2lt/item/ResearchNoteItem.java");
        String generatedBranch = item.substring(
                item.indexOf("// 已生成笔记:"),
                item.indexOf("@Override", item.indexOf("// 已生成笔记:")));

        int rebuild = generatedBranch.indexOf("applyGeneratedState(heldStack, data)");
        int snapshot = generatedBranch.indexOf("new OpenResearchNotePacket(heldStack.copy())");

        assertFalse(item.contains("ClientboundOpenBookPacket"));
        assertTrue(rebuild >= 0, "Legacy notes must have their written-book NBT rebuilt");
        assertTrue(snapshot > rebuild, "The server must send the rebuilt ItemStack snapshot");
        assertTrue(generatedBranch.contains("PacketSender.sendToPlayer(serverPlayer,"));
    }

    @Test
    void packetAndBridgeRemainCommonSideWhileTheBootstrapOpensWrittenBookAccess() throws Exception {
        String packet = source("src/main/java/com/moakiee/ae2lt/network/OpenResearchNotePacket.java");
        String bridge = source("src/main/java/com/moakiee/ae2lt/network/ResearchNoteClientBridge.java");
        String bootstrap = source("src/main/java/com/moakiee/ae2lt/client/ResearchNoteClientBootstrap.java");
        String clientInit = source("src/main/java/com/moakiee/ae2lt/client/LightningKeyClientInit.java");

        assertFalse(packet.contains("net.minecraft.client"));
        assertFalse(packet.contains("com.moakiee.ae2lt.client"));
        assertFalse(bridge.contains("net.minecraft.client"));
        assertFalse(bridge.contains("com.moakiee.ae2lt.client"));
        assertTrue(packet.contains("ResearchNoteClientBridge.open(packet.book())"));
        assertTrue(bootstrap.contains("book.getItem() instanceof ResearchNoteItem"));
        assertTrue(bootstrap.contains("new BookViewScreen.WrittenBookAccess(book)"));
        assertFalse(bootstrap.contains("BookAccess.fromItem"));
        assertTrue(clientInit.contains("ResearchNoteClientBootstrap.install()"));
    }

    @Test
    void openPacketIsAppendedAsAClientboundProtocolThreeMessage() throws Exception {
        String network = source("src/main/java/com/moakiee/ae2lt/network/NetworkInit.java");
        List<String> expectedOrder = List.of(
                "WirelessConnectorUsePacket",
                "FrequencyCardUsePacket",
                "ToggleFrequencyCardAutoConnectPacket",
                "MatrixControllerActionPacket",
                "TianshuControllerActionPacket",
                "OpenFrequencyMenuPacket",
                "CreateFrequencyPacket",
                "DeleteFrequencyPacket",
                "EditFrequencyPacket",
                "SelectFrequencyPacket",
                "ChangeMemberPacket",
                "EasterEggPacket",
                "SyncFrequencyListPacket",
                "SyncFrequencyDetailPacket",
                "UpdateFrequencyBasicPacket",
                "FrequencyResponsePacket",
                "PigmeeAssemblerAnimationPacket",
                "RitualItemBurstPacket",
                "CelestweaveSubmoduleActivePacket",
                "FlightInertiaSyncPacket",
                "PhaseLockProtectionSyncPacket",
                "ShieldHitFeedbackSuppressionPacket",
                "RailgunBeamTogglePacket",
                "RailgunFirePacket",
                "RailgunBeamUpdatePacket",
                "RailgunBeamChainFxPacket",
                "RailgunRecoilFxPacket",
                "DashPacket",
                "PhaseFlightInputPacket",
                "OpenDeviceHubPacket",
                "DeviceHubActionPacket",
                "DeviceHubSyncPacket",
                "OpenMaintenanceEditorPacket",
                "SaveMaintenanceRulePacket",
                "SaveGlobalReservePacket",
                "RequestUploadTargetsPacket",
                "RequestClosedLoopResultPagePacket",
                "UploadPatternToTargetPacket",
                "MaintenanceEditorSyncPacket",
                "MaintenanceSummarySyncPacket",
                "UploadTargetsSyncPacket",
                "ClosedLoopResultPagePacket",
                "OpenResearchNotePacket");
        var matcher = Pattern.compile("(?m)^\\s+([A-Za-z0-9]+Packet)\\.class,$").matcher(network);
        List<String> actualOrder = new ArrayList<>();
        while (matcher.find()) {
            actualOrder.add(matcher.group(1));
        }
        int openPacket = network.indexOf("OpenResearchNotePacket.class");
        int direction = network.indexOf("Optional.of(NetworkDirection.PLAY_TO_CLIENT)", openPacket);

        assertTrue(network.contains("PROTOCOL_VERSION = \"3\""));
        assertTrue(direction > openPacket, "The research-note packet must only travel to clients");
        assertEquals(expectedOrder, actualOrder,
                "Existing packet discriminators must remain stable and the new packet must stay last");
    }

    private static String source(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
