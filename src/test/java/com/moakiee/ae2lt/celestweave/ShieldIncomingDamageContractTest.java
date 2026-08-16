package com.moakiee.ae2lt.celestweave;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class ShieldIncomingDamageContractTest {

    @Test
    void shieldsDecideAndPayAtTheEndOfIncomingDamage() throws Exception {
        String source = handlerSource();
        int incomingStart = source.indexOf("public static void onShieldIncoming");
        int preStart = source.indexOf("public static void onPre");
        String incoming = source.substring(incomingStart, preStart);

        assertTrue(annotationBefore(source, incomingStart).contains(
                "@SubscribeEvent(priority = EventPriority.LOWEST)"));
        assertTrue(incoming.contains("float incoming = event.getAmount()"));
        assertTrue(incoming.contains("payMitigationLightning(player, mitigation, staged"));
        assertTrue(incoming.contains("event.setAmount(afterMitigation)"));
        assertTrue(incoming.contains("event.setCanceled(true)"));
        assertTrue(incoming.indexOf("payMitigationLightning") < incoming.indexOf("event.setCanceled(true)"));
    }

    @Test
    void preDoesNotRecalculateShieldMitigation() throws Exception {
        String source = handlerSource();
        int preStart = source.indexOf("public static void onPre");
        int collectMitigationStart = source.indexOf("private static ActiveCapability collectMitigation", preStart);
        String pre = source.substring(preStart, collectMitigationStart);

        assertTrue(annotationBefore(source, preStart).contains(
                "@SubscribeEvent(priority = EventPriority.LOWEST)"));
        assertTrue(pre.contains("reflectIncomingDamage("));
        assertFalse(pre.contains("ArmorMitigationRules.apply("));
        assertFalse(pre.contains("payMitigationLightning("));
        assertFalse(pre.contains("setNewDamage("));
    }

    private static String handlerSource() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/celestweave/CelestweaveArmorDamageHandler.java"));
    }

    private static String annotationBefore(String source, int methodStart) {
        int annotationStart = source.lastIndexOf("@SubscribeEvent", methodStart);
        return source.substring(annotationStart, methodStart);
    }
}
