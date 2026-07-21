package com.moakiee.ae2lt.logic.railgun;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class RailgunTargetRulesContractTest {

    @Test
    void sharedRulesKeepPlayerStateInsideThePlayerBranch() throws Exception {
        String rules = source("RailgunTargetRules.java");
        String playerBranch = rules.substring(
                rules.indexOf("if (target instanceof Player targetPlayer)"),
                rules.indexOf("if (target instanceof LivingEntity living)"));
        String livingBranch = rules.substring(
                rules.indexOf("if (target instanceof LivingEntity living)"),
                rules.indexOf("return target.canBeHitByProjectile()"));

        assertTrue(playerBranch.contains("targetPlayer.isFakePlayer()"));
        assertTrue(playerBranch.contains("targetPlayer.isSpectator()"));
        assertTrue(playerBranch.contains("targetPlayer.isCreative()"));
        assertFalse(livingBranch.contains("isFakePlayer()"));
        assertFalse(livingBranch.contains("isSpectator()"));
        assertFalse(livingBranch.contains("isCreative()"));
        assertTrue(rules.contains("return target.canBeHitByProjectile()"));
    }

    @Test
    void allServerDamageEntrypointsReuseTheSharedRules() throws Exception {
        String fire = source("RailgunFireService.java");
        String execution = source("OverloadExecutionService.java");
        String beam = source("RailgunBeamService.java");
        String chain = source("RailgunChainResolver.java");

        assertTrue(fire.contains("e -> RailgunTargetRules.canAffect(player, e, allowPlayerTargets)"));
        assertTrue(fire.contains("RailgunTargetRules.canAffect(player, target, allowPlayerTargets)"));
        assertTrue(execution.contains("RailgunTargetRules.canAffect(player, target, allowPlayerTargets)"));
        assertTrue(beam.contains("RailgunTargetRules.canAffect(player, e, pvp)"));
        assertTrue(chain.contains("RailgunTargetRules.canAffect(null, entity, pvp)"));
    }

    @Test
    void nonLivingExecutionIsDirectOnlyAndRunsGenericCleanup() throws Exception {
        String fire = source("RailgunFireService.java");
        String execution = source("OverloadExecutionService.java");
        String cleanup = execution.substring(
                execution.indexOf("private static void forceRemoveNonLiving("),
                execution.indexOf("private static void establishKillCredit("));

        assertTrue(fire.contains("directTarget != null && !(directTarget instanceof LivingEntity)"));
        assertTrue(fire.contains("applyDirectNonLivingHit("));
        assertTrue(fire.contains("OverloadExecutionService.onDirectNonLivingHit("));
        assertTrue(cleanup.contains("target.kill()"));
        assertTrue(cleanup.contains("target.discard()"));
        assertTrue(cleanup.contains("target.remove(Entity.RemovalReason.KILLED)"));
        assertTrue(cleanup.indexOf("target.kill()") < cleanup.indexOf("target.discard()"));
        assertTrue(cleanup.indexOf("target.discard()")
                < cleanup.indexOf("target.remove(Entity.RemovalReason.KILLED)"));
        assertFalse(execution.contains("GuardianCrystalEntity"));
        assertFalse(execution.contains("draconicevolution"));
    }

    @Test
    void blockImpactPromotesTheNearestLegalEntityToPrimary() throws Exception {
        String fire = source("RailgunFireService.java");
        String defaults = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/config/RailgunDefaults.java"));
        String recovery = fire.substring(
                fire.indexOf("private static Entity findImpactPrimary("),
                fire.indexOf("private static void broadcastFire("));

        assertTrue(fire.contains("&& directTarget == null"));
        assertTrue(fire.contains("&& bhr.getType() == HitResult.Type.BLOCK"));
        assertTrue(fire.contains("directTarget = findImpactPrimary("));
        assertTrue(fire.contains("tier == RailgunChargeTier.EHV3"));
        assertTrue(fire.contains("mods.hasOverloadExecution()"));
        assertTrue(fire.contains("settings.overloadImpactTargeting()"));
        assertTrue(fire.contains("if (directTarget instanceof LivingEntity primary)"));
        assertTrue(recovery.contains("RailgunTargetRules.canAffect(player, entity, allowPlayerTargets)"));
        assertTrue(recovery.contains("candidate.getBoundingBox().getCenter().distanceToSqr(impact)"));
        assertTrue(defaults.contains("IMPACT_PRIMARY_SEARCH_HALF_EXTENT = 3.5D"));
    }

    private static String source(String name) throws Exception {
        return Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/logic/railgun", name));
    }
}
