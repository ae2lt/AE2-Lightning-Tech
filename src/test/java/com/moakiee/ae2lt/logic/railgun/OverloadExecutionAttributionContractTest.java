package com.moakiee.ae2lt.logic.railgun;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class OverloadExecutionAttributionContractTest {
    @Test
    void normalExecutionKeepsPlayerAttributionAndLootFallback() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/logic/railgun/OverloadExecutionService.java"));
        String fireService = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/logic/railgun/RailgunFireService.java"));
        String normalDeath = source.substring(
                source.indexOf("private static void completeNormalDeath("),
                source.indexOf("private static void forceRemove("));

        assertTrue(fireService.indexOf("target.hurt(ds, (float) finalDamage)")
                < fireService.indexOf("OverloadExecutionService.onHit("));
        assertTrue(source.contains("establishKillCredit(target, source)"));
        assertTrue(normalDeath.contains("target.die(source)"));
        assertFalse(normalDeath.contains("target.kill()"));
        assertFalse(normalDeath.contains("target.discard()"));
        assertFalse(normalDeath.contains("target.remove("));
        assertFalse(normalDeath.contains("target.setRemoved("));
        assertTrue(source.contains("srcEntity.killedEntity(sl, victim)"));
        assertTrue(source.contains("victim.dropAllDeathLoot(sl, source)"));
        assertTrue(source.contains("normalDeathCompleted(target, playerDeathsBefore)"));
        assertFalse(source.contains("player.killedEntity(level, target)"));
    }

    @Test
    void forcedRemovalRunsCleanupLayersAndNeverNamesBossTypes() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/logic/railgun/OverloadExecutionService.java"));
        String forcedRemoval = source.substring(
                source.indexOf("private static void forceRemove("),
                source.indexOf("private static void establishKillCredit("));

        assertTrue(forcedRemoval.contains("target.die(source)"));
        assertTrue(forcedRemoval.contains("target.kill()"));
        assertTrue(forcedRemoval.contains("target.discard()"));
        assertTrue(forcedRemoval.contains("target.remove(Entity.RemovalReason.KILLED)"));
        assertTrue(forcedRemoval.contains("target.setRemoved(Entity.RemovalReason.KILLED)"));
        assertTrue(forcedRemoval.indexOf("target.die(source)")
                < forcedRemoval.indexOf("target.kill()"));
        assertTrue(forcedRemoval.indexOf("target.kill()")
                < forcedRemoval.indexOf("target.discard()"));
        assertTrue(forcedRemoval.indexOf("target.discard()")
                < forcedRemoval.indexOf("target.remove(Entity.RemovalReason.KILLED)"));
        assertFalse(forcedRemoval.contains("if (target.isRemoved()) return"));
        assertFalse(forcedRemoval.contains("dropAllDeathLoot"));

        assertFalse(source.contains("EnderDragon"));
        assertFalse(source.contains("WitherBoss"));
        assertFalse(source.contains("GaiaGuardian"));
        assertFalse(source.contains("DraconicGuardian"));
        assertFalse(source.contains("SunSpirit"));
    }

    @Test
    void forceSwitchCannotRemoveServerPlayers() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/logic/railgun/OverloadExecutionService.java"));

        assertTrue(source.contains("forceRemoval && !(target instanceof ServerPlayer)"));
        assertTrue(source.contains("player.getStats().getValue(Stats.CUSTOM.get(Stats.DEATHS))"));
    }

    @Test
    void executionDoesNotPropagateThroughChainDamage() throws Exception {
        String resolver = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/logic/railgun/RailgunChainResolver.java"));
        String fireService = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/logic/railgun/RailgunFireService.java"));

        assertTrue(resolver.contains("boolean chainPropagation"));
        assertTrue(resolver.contains("chainStartAt, true"));
        assertTrue(fireService.contains("overloadEligible && !hit.chainPropagation()"));
        assertFalse(fireService.contains("target instanceof Player && !paralyzePlayers) continue"));
    }
}
