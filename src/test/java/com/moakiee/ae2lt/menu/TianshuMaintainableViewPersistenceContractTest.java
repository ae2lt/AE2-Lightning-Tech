package com.moakiee.ae2lt.menu;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class TianshuMaintainableViewPersistenceContractTest {
    private static final Path JAVA_ROOT = Path.of("src/main/java/com/moakiee/ae2lt");

    @Test
    void wiredAndWirelessHostsPersistTheCustomViewSelection() throws Exception {
        for (String relativePath : List.of(
                "part/TianshuPatternEncodingTerminalPart.java",
                "logic/tianshu/terminal/TianshuWirelessPatternEncodingTermMenuHost.java",
                "integration/ae2wtlib/TianshuWTMenuHost.java")) {
            String host = Files.readString(JAVA_ROOT.resolve(relativePath));
            assertTrue(host.contains("TAG_MAINTAINABLE_VIEW"), relativePath);
            assertTrue(host.contains("data.getBoolean(TAG_MAINTAINABLE_VIEW)"), relativePath);
            assertTrue(host.contains("data.putBoolean(TAG_MAINTAINABLE_VIEW, maintainableView)"),
                    relativePath);
            assertTrue(host.contains("void setMaintainableView(boolean enabled)"), relativePath);
        }
    }

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
