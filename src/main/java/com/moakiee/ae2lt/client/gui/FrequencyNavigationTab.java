package com.moakiee.ae2lt.client.gui;

/**
 * Navigation tabs for the frequency GUI, mirroring Flux Networks' tab structure.
 */
public enum FrequencyNavigationTab {
    TAB_HOME("ae2lt.gui.tab.home"),
    TAB_SELECTION("ae2lt.gui.tab.selection"),
    TAB_CONNECTION("ae2lt.gui.tab.connection"),
    TAB_MEMBER("ae2lt.gui.tab.member"),
    TAB_SETTING("ae2lt.gui.tab.setting"),
    TAB_CREATE("ae2lt.gui.tab.create");

    public static final FrequencyNavigationTab[] VALUES = values();

    private final String translationKey;

    FrequencyNavigationTab(String translationKey) {
        this.translationKey = translationKey;
    }

    public String getTranslationKey() {
        return translationKey;
    }
}
