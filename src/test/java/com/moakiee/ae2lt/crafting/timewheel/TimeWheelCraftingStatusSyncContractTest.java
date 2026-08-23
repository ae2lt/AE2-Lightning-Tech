package com.moakiee.ae2lt.crafting.timewheel;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class TimeWheelCraftingStatusSyncContractTest {
    private static final Path MIXIN = Path.of(
            "src/main/java/com/moakiee/ae2lt/mixin/thunderbolt/TimeWheelCraftingCPUMenuMixin.java");

    @Test
    void finishedJobReplacesTheIncrementalClientViewUsingRefactorSemantics() throws Exception {
        String source = Files.readString(MIXIN).replace("\r\n", "\n");

        assertTrue(source.contains("this.thunderbolt$jobPresent && !jobPresent"),
                "The menu must detect the active-job to terminal transition");
        assertTrue(source.contains("this.incrementalUpdateHelper.reset();\n"
                        + "            thunderbolt$queueAllItems(logic);"),
                "The terminal transition must replace stale planned entries with a full snapshot");
        assertTrue(source.contains("tracker.getRemainingItemCount()"));
        assertTrue(source.contains("tracker.getStartItemCount()"),
                "The Forge port must retain the 1.21 refactor's progress-header semantics");
    }
}
