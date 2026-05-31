package com.moakiee.ae2lt.api.pattern;

/**
 * Optional UI contract for OverloadedPatternProviderBlockEntity subclasses that
 * reuse AE2LT's overloaded pattern provider menu.
 */
public interface PatternProviderUiProfile {
    String DEFAULT_TITLE_TRANSLATION_KEY = "ae2lt.gui.title.overloaded_pattern_provider";

    default boolean ae2lt$isPackagedProviderUi() {
        return false;
    }

    default boolean ae2lt$isModeSwitchVisible() {
        return true;
    }

    default boolean ae2lt$isFilteredImportVisible() {
        return true;
    }

    default boolean ae2lt$isWirelessTuningVisible() {
        return true;
    }

    default boolean ae2lt$isBlockingModeVisible() {
        return true;
    }

    default String ae2lt$titleTranslationKey() {
        return DEFAULT_TITLE_TRANSLATION_KEY;
    }
}
