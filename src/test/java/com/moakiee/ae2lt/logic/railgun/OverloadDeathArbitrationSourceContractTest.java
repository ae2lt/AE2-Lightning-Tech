package com.moakiee.ae2lt.logic.railgun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class OverloadDeathArbitrationSourceContractTest {
    @Test
    void finalDeathListenerPreservesCelestweaveBeforeUncancelingExecution() throws Exception {
        String handler = source(
                "src/main/java/com/moakiee/ae2lt/logic/railgun/OverloadExecutionDeathHandler.java");

        assertTrue(handler.contains(
                "@SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)"));
        assertTrue(handler.contains("OverloadExecutionContext.contains(event.getEntity())"));
        assertTrue(handler.contains(
                "CelestweaveArmorUndyingHandler.wasProtectedThisTick(event.getEntity())"));
        assertTrue(handler.indexOf("wasProtectedThisTick")
                < handler.indexOf("event.setCanceled(false)"));
        assertFalse(handler.contains("DamageSource"));
    }

    @Test
    void executionDeathScopeIsIdentityBasedAndAlwaysClosed() throws Exception {
        String context = source(
                "src/main/java/com/moakiee/ae2lt/logic/railgun/OverloadExecutionContext.java");
        String service = source(
                "src/main/java/com/moakiee/ae2lt/logic/railgun/OverloadExecutionService.java");
        String scopedDeath = "try (var ignored = OverloadExecutionContext.enter(target))";

        assertTrue(context.contains("ThreadLocal<IdentityHashMap<LivingEntity, Integer>>"));
        assertTrue(context.contains("implements AutoCloseable"));
        assertTrue(context.contains("ACTIVE.remove()"));
        assertEquals(2, occurrences(service, scopedDeath));
        assertEquals(2, occurrences(service, "target.die(source);"));
    }

    @Test
    void copiedDeathLootAndAnimationAreSuppressedWithoutExternalModReferences() throws Exception {
        String armorHandler = source(
                "src/main/java/com/moakiee/ae2lt/celestweave/CelestweaveArmorUndyingHandler.java");
        String entityMixin = source(
                "src/main/java/com/moakiee/ae2lt/mixin/EntityUndyingMixin.java");
        String livingMixin = source(
                "src/main/java/com/moakiee/ae2lt/mixin/LivingEntityUndyingMixin.java");
        String levelMixin = source(
                "src/main/java/com/moakiee/ae2lt/mixin/ServerLevelMixin.java");
        String packetMixin = source(
                "src/main/java/com/moakiee/ae2lt/mixin/ServerCommonPacketListenerUndyingMixin.java");
        String mixinConfig = source("src/main/resources/ae2lt.mixins.json");

        assertTrue(armorHandler.contains("data.contains(TAG_PROTECTED_TICK)"));
        assertTrue(armorHandler.contains("protectBeforeDeathSideEffect(ServerPlayer player)"));
        assertTrue(armorHandler.contains("ArmorEnergyService.consumeActiveCostPayment("));

        assertTrue(entityMixin.contains(
                "gameEvent(Lnet/minecraft/core/Holder;Lnet/minecraft/world/entity/Entity;)V"));
        assertTrue(entityMixin.contains("gameEvent == GameEvent.ENTITY_DIE"));
        assertTrue(entityMixin.contains(
                "CelestweaveArmorUndyingHandler.protectBeforeDeathSideEffect(player)"));

        assertTrue(livingMixin.contains("@Inject(method = \"dropAllDeathLoot\""));
        assertTrue(livingMixin.contains(
                "CelestweaveArmorUndyingHandler.protectBeforeDeathSideEffect(player)"));

        assertTrue(levelMixin.contains("@Inject(method = \"broadcastEntityEvent\""));
        assertTrue(levelMixin.contains("eventId == EntityEvent.DEATH"));
        assertTrue(levelMixin.contains(
                "CelestweaveArmorUndyingHandler.protectBeforeDeathSideEffect(player)"));

        assertTrue(packetMixin.contains("ClientboundPlayerCombatKillPacket"));
        assertTrue(packetMixin.contains("ServerGamePacketListenerImpl gameListener"));
        assertTrue(packetMixin.contains(
                "CelestweaveArmorUndyingHandler.protectBeforeDeathSideEffect(gameListener.player)"));
        assertTrue(packetMixin.contains(
                "Lnet/minecraft/network/PacketSendListener;)V"));
        assertTrue(mixinConfig.contains("\"ServerCommonPacketListenerUndyingMixin\""));
        assertTrue(mixinConfig.contains("\"EntityUndyingMixin\""));

        assertFalse((armorHandler + entityMixin + livingMixin + levelMixin + packetMixin)
                .toLowerCase()
                .contains("avaritia"));
    }

    private static String source(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }
}
