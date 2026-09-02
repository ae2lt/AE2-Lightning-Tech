package com.moakiee.ae2lt.grid.wirelesslink;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Ae2csCompatibilitySourceContractTest {
    private static final Path MAIN_JAVA = Path.of("src/main/java");

    @Test
    void targetsOnlyAe2csReceiverRebuildAndUsesEndpointIdentity() throws Exception {
        String mixin = Files.readString(MAIN_JAVA.resolve(
                "com/moakiee/ae2lt/mixin/BroadcastFrequencyBandMixin.java"));
        String config = Files.readString(Path.of("src/main/resources/ae2lt.mixins.json"));
        String plugin = Files.readString(MAIN_JAVA.resolve(
                "com/moakiee/ae2lt/mixin/AE2LTMixinConfigPlugin.java"));

        assertTrue(mixin.contains(
                "io.github.lounode.ae2cs.api.linker.broadcast.BroadcastFrequencyBand"));
        assertTrue(mixin.contains(
                "applyReceiver(Lnet/minecraft/core/GlobalPos;Lappeng/api/networking/IGridNode;Lio/github/lounode/ae2cs/api/CustomChannelProviderHost;I)V"));
        assertTrue(mixin.contains("WirelessLinkOps.findConnection(controllerNode, receiverNode)"));
        assertTrue(mixin.contains("!existing.isInWorld()"));
        assertTrue(mixin.contains("return original.call(controllerNode, receiverNode);"));
        assertTrue(config.contains("BroadcastFrequencyBandMixin"));
        assertTrue(plugin.contains("\"BroadcastFrequencyBandMixin\", \"ae2cs\""));
    }

    @Test
    void doesNotSwallowPhysicalOrUnrelatedConnectionFailures() throws Exception {
        String mixin = Files.readString(MAIN_JAVA.resolve(
                "com/moakiee/ae2lt/mixin/BroadcastFrequencyBandMixin.java"));
        assertFalse(mixin.contains("catch (IllegalStateException"));
        assertFalse(mixin.contains("GridConnection.create"));
        assertFalse(mixin.contains("Throwable"));
    }
}
