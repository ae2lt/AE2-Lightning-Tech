package com.moakiee.ae2lt.mixin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class CelestweaveCreativeSyncSourceContractTest {
    @Test
    void staleCreativeEchoesUseServerAuthorityForPhaseProjections() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/mixin/ServerGamePacketListenerCelestweaveCreativeSyncMixin.java"));

        assertTrue(source.contains("instanceof BaseCelestweaveArmorItem"));
        assertTrue(source.contains("instanceof PhaseLockProjectionItem"));
        assertTrue(source.contains(
                "PhaseLockService.hasPrivateArmor(player, projection.equipmentSlot())"));
        assertFalse(source.contains("PHASE_LOCK_PROJECTION_LINK.get(uploaded)"));
        assertTrue(source.contains("setRemoteSlot(slot.index, ItemStack.EMPTY)"));
    }
}
