package com.moakiee.ae2lt.item.railgun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.moakiee.ae2lt.device.DeviceKind;
import com.moakiee.ae2lt.device.DeviceSlotType;

final class MultidimensionalExecutionContractTest {
    @Test
    void occupiesTheSingleExecutionSlot() {
        assertEquals(1, RailgunModuleItem.maxInstallAmount(
                RailgunModuleType.MULTIDIMENSIONAL_EXECUTION));
        assertTrue(RailgunModuleItem.accepts(
                RailgunModuleType.MULTIDIMENSIONAL_EXECUTION,
                DeviceKind.RAILGUN,
                DeviceSlotType.OVERLOAD_EXECUTION));
        assertFalse(RailgunModuleItem.accepts(
                RailgunModuleType.MULTIDIMENSIONAL_EXECUTION,
                DeviceKind.CELESTWEAVE_CORE,
                DeviceSlotType.CHEST_MODULE));
    }

    @Test
    void resolvesAllThreeModesBeforeHealthAndResourceChecks() throws Exception {
        String service = source(
                "src/main/java/com/moakiee/ae2lt/logic/railgun/OverloadExecutionService.java");
        int onHit = service.indexOf("public static void onHit(");
        int nonLiving = service.indexOf("public static void onDirectNonLivingHit(", onHit);
        String livingPath = service.substring(onHit, nonLiving);

        int multidimensional = livingPath.indexOf("mods.hasMultidimensionalExecution()");
        int ordinaryModule = livingPath.indexOf("if (!mods.hasOverloadExecution())");
        int maxHealth = livingPath.indexOf("target.getMaxHealth()");
        int feCost = livingPath.indexOf("RailgunEnergyRules.overloadExecutionCostFe()");
        assertTrue(multidimensional >= 0 && multidimensional < maxHealth);
        assertTrue(multidimensional < feCost);

        String multidimensionalBranch = livingPath.substring(multidimensional, ordinaryModule);
        assertTrue(multidimensionalBranch.contains("OFF_MULTIDIMENSIONAL_DAMAGE"));
        assertTrue(multidimensionalBranch.contains("applyOrdinaryDamage"));
        assertTrue(multidimensionalBranch.contains("execute("));
        assertTrue(multidimensionalBranch.contains("return"));
        assertFalse(multidimensionalBranch.contains("RailgunEnergyBuffer.tryConsume"));
        assertFalse(multidimensionalBranch.contains("ArmorLightningService"));

        String ordinaryOffBranch = livingPath.substring(ordinaryModule, maxHealth);
        assertTrue(ordinaryOffBranch.contains("OFF_OVERLOAD_DAMAGE"));
        assertTrue(ordinaryOffBranch.contains("applyOrdinaryDamage"));
        int ordinaryFallback = livingPath.indexOf("applyOrdinaryDamage", ordinaryModule);
        assertTrue(ordinaryFallback >= 0 && ordinaryFallback < feCost);
    }

    @Test
    void offModeUsesOrdinaryArmorPiercingDamageWithoutDeathOrRemovalCalls() throws Exception {
        String service = source(
                "src/main/java/com/moakiee/ae2lt/logic/railgun/OverloadExecutionService.java");
        int methodStart = service.indexOf("private static void applyOrdinaryDamage(");
        int executeStart = service.indexOf("private static void execute(", methodStart);
        String method = service.substring(methodStart, executeStart);

        assertTrue(service.contains("private static final float OFF_OVERLOAD_DAMAGE = 600.0F"));
        assertTrue(service.contains(
                "private static final float OFF_MULTIDIMENSIONAL_DAMAGE = Float.MAX_VALUE"));
        assertTrue(method.contains("target.hurt(source, damage)"));
        assertFalse(method.contains("setHealth"));
        assertFalse(method.contains(".die("));
        assertFalse(method.contains(".kill("));
        assertFalse(method.contains(".remove("));
    }

    @Test
    void ordinaryAndMultidimensionalExecutionCannotBeInstalledTogether() throws Exception {
        String storage = source(
                "src/main/java/com/moakiee/ae2lt/item/railgun/RailgunModuleStorage.java");

        assertTrue(storage.contains("module.moduleType() == RailgunModuleType.OVERLOAD_EXECUTION"));
        assertTrue(storage.contains(
                "module.moduleType() == RailgunModuleType.MULTIDIMENSIONAL_EXECUTION"));
        assertTrue(storage.contains("entries.hasAnyExecution()"));
    }

    private static String source(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
