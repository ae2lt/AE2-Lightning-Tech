package com.moakiee.ae2lt.celestweave;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class CelestweaveUnlimitedModulesSourceContractTest {
    @Test
    void armorHasNoAggregateModuleCapacityOrPersistenceTruncation() throws Exception {
        String armorPart = read("src/main/java/com/moakiee/ae2lt/celestweave/ArmorPart.java");
        String armorState = read("src/main/java/com/moakiee/ae2lt/celestweave/CelestweaveArmorState.java");
        String persistence = read(
                "src/main/java/com/moakiee/ae2lt/celestweave/state/ArmorPersistentData.java");

        assertFalse(armorPart.contains("moduleSlotCount"));
        assertFalse(armorState.contains("MAX_MODULE_TYPES"));
        assertFalse(armorState.contains("getInstalledUnitCount"));
        assertFalse(persistence.contains("MAX_MODULE_TYPES"));
        assertFalse(persistence.contains("writtenUnits"));
        assertTrue(persistence.contains("for (ItemStack stack : merged.values())"));
    }

    @Test
    void guideDoesNotDisplayModuleSlotCounts() throws Exception {
        String english = read("src/main/resources/assets/ae2lt/ae2guide/celestweave.md");
        String chinese = read("src/main/resources/assets/ae2lt/ae2guide/_zh_cn/celestweave.md");

        assertFalse(english.contains("| Module Slots |"));
        assertFalse(chinese.contains("| 模块槽 |"));
        assertTrue(english.contains("accepts any number of compatible modules"));
        assertTrue(chinese.contains("可以安装任意数量的兼容模块"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
