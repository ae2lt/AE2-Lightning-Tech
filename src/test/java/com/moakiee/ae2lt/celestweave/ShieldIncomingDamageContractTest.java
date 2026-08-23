package com.moakiee.ae2lt.celestweave;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class ShieldIncomingDamageContractTest {

    @Test
    void shieldsDecideAndPayBeforeVanillaDamageProcessing() throws Exception {
        String source = handlerSource();
        String mixin = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/mixin/LivingEntityIncomingDamageMixin.java"));
        int incomingStart = source.indexOf("public static IncomingDamageResult onIncomingDamage");
        int preStart = source.indexOf("public static void onPre");
        String incoming = source.substring(incomingStart, preStart);

        assertFalse(source.contains("LivingHurtEvent"));
        assertTrue(mixin.contains("LivingEntity;isSleeping()Z"));
        assertTrue(mixin.contains("@Local(argsOnly = true) LocalFloatRef mutableAmount"));
        assertTrue(mixin.contains("mutableAmount.set(result.amount())"));
        assertTrue(mixin.contains("cir.setReturnValue(false)"));
        assertFalse(mixin.contains("@WrapMethod"));
        assertTrue(mixin.contains("@WrapOperation("));
        assertTrue(mixin.contains("LivingEntity;actuallyHurt"));
        assertTrue(mixin.contains("beginOriginalDamage(entity, originalDamage.get())"));
        assertTrue(mixin.contains("finishOriginalDamage(entity, initialDepth)"));
        assertTrue(incoming.contains("DeviceCapability.DamageTypeImmunity"));
        assertTrue(incoming.contains("payMitigationLightning(player, mitigation, staged"));
        assertTrue(incoming.contains("IncomingDamageResult.pass(afterMitigation)"));
        assertTrue(incoming.contains("IncomingDamageResult.cancel()"));
        int payment = incoming.indexOf("payMitigationLightning");
        int paidCancellation = incoming.indexOf("return IncomingDamageResult.cancel()", payment);
        assertTrue(payment >= 0 && paidCancellation > payment);
    }

    @Test
    void finalDamageDoesNotRecalculateShieldMitigation() throws Exception {
        String source = handlerSource();
        int preStart = source.indexOf("public static void onPre");
        int collectMitigationStart = source.indexOf("private static ActiveCapability collectMitigation", preStart);
        String pre = source.substring(preStart, collectMitigationStart);

        assertTrue(annotationBefore(source, preStart).contains(
                "@SubscribeEvent(priority = EventPriority.LOWEST)"));
        assertTrue(pre.contains("reflectIncomingDamage("));
        assertTrue(pre.contains("currentIncomingDamage(player, event.getAmount())"));
        assertFalse(pre.contains("ArmorMitigationRules.apply("));
        assertFalse(pre.contains("payMitigationLightning("));
        assertFalse(pre.contains("setAmount("));
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
