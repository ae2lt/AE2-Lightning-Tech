package com.moakiee.ae2lt.overload.armor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class ArmorUndyingRestoreRulesTest {

    @Test
    void undyingRestoresFullHealthWithoutPurifyingStatusEffects() throws Exception {
        String handlerSource = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/overload/armor/OverloadArmorUndyingHandler.java"));
        String compactHandler = handlerSource.replaceAll("\\s+", "");

        assertFalse(
                handlerSource.contains("MobEffectCategory"),
                "Undying should not inspect effect categories after triggering.");
        assertFalse(
                compactHandler.contains("purifyEffects(player"),
                "Undying should leave status effects untouched after triggering.");
        assertFalse(
                compactHandler.contains("Math.min(player.getMaxHealth(),RESTORE_HEALTH)"),
                "Undying should no longer restore only a small fixed health amount.");
        assertTrue(
                compactHandler.contains("floattargetHealth=Math.max(1.0F,player.getMaxHealth());"),
                "Undying should restore the player to full max health.");
        assertTrue(
                compactHandler.contains("player.setHealth(targetHealth);"),
                "The full-health restore should write the target health back to the player.");
    }
}
