package com.moakiee.ae2lt.menu;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class TianshuMaintainableViewPersistenceContractTest {
    private static final Path JAVA_ROOT = Path.of("src/main/java/com/moakiee/ae2lt");

    // Persistence is exercised by TianshuTerminalStateTest and TianshuTerminalStateGameTests.
    // This remaining source guard only covers the menu's persistent-versus-temporary UI wiring.
    @Test
    void menusRestoreTheHostSelectionAndOnlyPersistUserChanges() throws Exception {
        String menu = Files.readString(JAVA_ROOT.resolve(
                "menu/TianshuPatternEncodingTermMenu.java"));
        String overview = Files.readString(JAVA_ROOT.resolve(
                "client/TianshuGlobalReserveScreen.java"));

        assertTrue(menu.contains("this.maintainableView = host.isMaintainableView()"));
        assertTrue(menu.contains("tianshuHost.setMaintainableView(enabled)"));
        assertTrue(menu.contains("applyMaintainableViewServer(enabled, true)"));
        assertTrue(menu.contains("applyMaintainableViewServer(enabled, false)"));
        assertTrue(overview.contains("menu.setMaintainableViewTemporarily(false)"));
        assertTrue(overview.contains("menu.setMaintainableViewTemporarily(true)"));
    }
}
