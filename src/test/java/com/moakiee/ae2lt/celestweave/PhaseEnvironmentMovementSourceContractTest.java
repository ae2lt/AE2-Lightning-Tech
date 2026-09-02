package com.moakiee.ae2lt.celestweave;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class PhaseEnvironmentMovementSourceContractTest {
    @Test
    void forceScopesArePlayerBoundFinallySafeAndSeparatedFromTeleport() throws Exception {
        String guard = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/celestweave/PhaseFlightMovementGuard.java"));

        assertTrue(guard.contains("ENVIRONMENT_MOVEMENT_DEPTH"));
        assertTrue(guard.contains("runAsEnvironmentMovement(Player player, Runnable movement)"));
        assertTrue(guard.contains("VANILLA_TRAVEL_MOVEMENT_DEPTH"));
        assertTrue(guard.contains("runAsVanillaTravelMovement(Player player, Runnable movement)"));
        assertTrue(guard.contains("consumeVanillaTravelMovement(player)"));
        assertTrue(guard.contains("VANILLA_TRAVEL_SCOPE_DEPTH"));
        assertTrue(guard.contains("runInVanillaTravelScope(Player player, Runnable travel)"));
        assertTrue(guard.contains("isVanillaTravelScopeActive(Player player)"));
        assertTrue(guard.contains("try {"));
        assertTrue(guard.contains("finally {"));
        assertFalse(guard.contains("isInWater() || isInLava()"));

        String teleportAuthorization = methodBody(
                guard,
                "public static boolean isSelfTeleportAuthorized",
                "private static boolean isPrivilegedCommandExecution");
        assertFalse(teleportAuthorization.contains("ENVIRONMENT_MOVEMENT_DEPTH"));
        assertFalse(teleportAuthorization.contains("VANILLA_TRAVEL_MOVEMENT_DEPTH"));
    }

    @Test
    void vanillaFluidAndBubbleSourcesUseNarrowEnvironmentScopes() throws Exception {
        String mixin = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/mixin/EntityPhaseMovementMixin.java"));

        assertTrue(mixin.contains("\"updateFluidHeightAndDoFluidPushing()V\""));
        assertTrue(mixin.contains(
                "\"updateFluidHeightAndDoFluidPushing(Ljava/util/function/Predicate;)V\""));
        assertTrue(mixin.contains("@WrapOperation"));
        assertTrue(mixin.contains("Object2ObjectMap;forEach"));
        assertTrue(mixin.contains("Object2ObjectArrayMap;forEach"));
        assertTrue(mixin.contains("remap = false"));
        assertTrue(mixin.contains("require = 0"));
        assertFalse(mixin.contains("MutableTriple"));
        assertFalse(mixin.contains("FluidCalcs"));
        assertFalse(mixin.contains("lambda$updateFluidHeightAndDoFluidPushing$"));
        assertTrue(mixin.contains("ForgeMod.WATER_TYPE.get()"));
        assertTrue(mixin.contains("ForgeMod.LAVA_TYPE.get()"));
        assertTrue(mixin.contains("onAboveBubbleCol(Z)V"));
        assertTrue(mixin.contains("onInsideBubbleColumn(Z)V"));
        assertTrue(mixin.contains("PhaseFlightMovementGuard.runAsEnvironmentMovement"));
        assertFalse(mixin.contains("isFreezing()"));
    }

    @Test
    void vanillaTravelAuthorizesOnlyItsDirectMutationCalls() throws Exception {
        String livingMixin = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/mixin/LivingEntityPhaseJumpMixin.java"));
        String playerMixin = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/mixin/PlayerPhaseFlightMixin.java"));

        assertTrue(livingMixin.contains("method = \"travel\""));
        assertTrue(livingMixin.contains("@WrapMethod(method = \"travel\")"));
        assertTrue(livingMixin.contains("PhaseFlightMovementGuard.runInVanillaTravelScope("));
        assertTrue(livingMixin.contains("LivingEntity;move("));
        assertTrue(livingMixin.contains("LivingEntity;moveRelative("));
        assertTrue(livingMixin.contains("handleRelativeFrictionAndCalculateMovement"));
        assertTrue(livingMixin.contains("LivingEntity;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"));
        assertTrue(livingMixin.contains("LivingEntity;setDeltaMovement(DDD)V"));
        assertTrue(livingMixin.contains("runAsScopedVanillaTravelMovement("));
        assertFalse(livingMixin.contains("moveInFluid"));

        assertTrue(playerMixin.contains("method = \"travel\""));
        assertTrue(playerMixin.contains("Player;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"));
        assertTrue(playerMixin.contains("Player;setDeltaMovement(DDD)V"));
        assertTrue(playerMixin.contains("runAsVanillaTravelMovement("));
        assertFalse(playerMixin.contains("@WrapMethod(method = \"travel\")"));
        assertFalse(playerMixin.contains("runAsSelfMovement("));
    }

    private static String methodBody(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex);
        assertTrue(startIndex >= 0 && endIndex > startIndex);
        return source.substring(startIndex, endIndex);
    }
}
