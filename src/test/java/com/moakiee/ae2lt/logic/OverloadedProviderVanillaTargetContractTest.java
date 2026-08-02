package com.moakiee.ae2lt.logic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class OverloadedProviderVanillaTargetContractTest {
    @Test
    void everyCustomNormalPathUsesAe2sActiveSideFilter() throws Exception {
        var accessor = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/mixin/PatternProviderLogicAccessor.java"));
        var logic = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/logic/OverloadedPatternProviderLogic.java"));
        var autoReturn = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/logic/OverloadedAutoReturnController.java"));

        assertTrue(accessor.contains("@Invoker(\"getActiveSides\")"));
        assertTrue(logic.contains("invokeGetActiveSides()"));
        assertFalse(logic.contains("overloadedHost.getTargets()"));
        assertTrue(autoReturn.contains("environment.normalTargetDirections()"));
        assertFalse(autoReturn.contains("environment.provider().getTargets()"));
    }
}
