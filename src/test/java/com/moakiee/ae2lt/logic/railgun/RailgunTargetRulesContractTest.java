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
        assertFalse(beam.contains("e -> e instanceof LivingEntity"));
        assertFalse(chain.contains("!entity.isInvulnerable()"));
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
    void chargedSplashReplacesBlockImpactPrimaryRecovery() throws Exception {
        String fire = source("RailgunFireService.java");
        String defaults = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/config/RailgunDefaults.java"));

        assertTrue(fire.contains("settings.chargedSplash() ? switch (tier)"));
        assertTrue(fire.contains("impactRadius * RailgunDefaults.OVERLOAD_EXECUTION_SPLASH_RADIUS_RATIO"));
        assertTrue(fire.contains("target.getId() == directTargetId"));
        assertFalse(fire.contains("findImpactPrimary("));
        assertFalse(defaults.contains("IMPACT_PRIMARY_SEARCH_HALF_EXTENT"));
        assertTrue(defaults.contains("OVERLOAD_EXECUTION_SPLASH_RADIUS_RATIO = 0.5D"));
    }

    @Test
    void ordinaryBeamCanStartChainDamageFromAnEmptyEndpoint() throws Exception {
        String fire = source("RailgunFireService.java");
        String beam = source("RailgunBeamService.java");
        String chain = source("RailgunChainResolver.java");

        assertTrue(beam.contains("chainOrigin = trace.endPoint()"));
        assertTrue(beam.contains("resolveChainFromPoint(level, player, chainOrigin, ctx)"));
        assertTrue(beam.contains("broadcastBeamChainFx(level, player, chainOrigin"));
        assertFalse(fire.contains("resolveChainFromPoint("));
        assertTrue(chain.contains("public static List<Hit> resolveChainFromPoint("));
        assertTrue(chain.contains("walkBranch(level, source, seed, ctx, visited, start)"));
    }

    private static String source(String name) throws Exception {
        return Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/logic/railgun", name));
    }
}
