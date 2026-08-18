package com.moakiee.ae2lt.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class MachinePlayerInventoryLayoutContractTest {
    private static final Path SCREEN_ROOT =
            Path.of("src/main/resources/assets/ae2/screens");

    @Test
    void importedMachineBackgroundsUseTheirTwoPixelHigherInventoryBaseline() throws Exception {
        String layout = Files.readString(
                SCREEN_ROOT.resolve("common/legacy_player_inventory.json"));

        assertTrue(layout.contains("\"bottom\": 84"));
        assertTrue(layout.contains("\"bottom\": 26"));
        assertTrue(layout.contains("\"bottom\": 95"));

        for (String screenName : List.of(
                "atmospheric_ionizer.json",
                "lightning_collector.json",
                "lightning_simulation_room.json",
                "overloaded_interface.json",
                "overloaded_pattern_provider.json",
                "pigmee_molecular_assembler.json")) {
            String screen = Files.readString(SCREEN_ROOT.resolve(screenName));
            assertTrue(screen.contains("common/legacy_player_inventory.json"), screenName);
            assertFalse(screen.contains("common/player_inventory.json"), screenName);
        }
    }

    @Test
    void nativeAe2BackgroundKeepsTheForge120InventoryBaseline() throws Exception {
        String screen = Files.readString(SCREEN_ROOT.resolve("tianshu_seed_storage.json"));

        assertTrue(screen.contains("common/player_inventory.json"));
        assertFalse(screen.contains("common/legacy_player_inventory.json"));
    }
}
