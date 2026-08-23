package com.moakiee.ae2lt.api.client;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.moakiee.ae2lt.client.SettingToggleButtonAccess;

/**
 * Client-side extension point for hiding left-toolbar buttons that other mods
 * inject into AE2LT's overloaded pattern provider screens.
 */
public final class PatternProviderToolbarButtonHider {
    /**
     * ExtendedAE Plus 1.5.5 for Forge 1.20.1 creates both its advanced-blocking
     * and smart-doubling controls with the same anonymous SettingToggleButton
     * subclass returned by GuiUtil#createToggle.
     */
    public static final String EXTENDED_AE_PLUS_SMART_FEATURE_BUTTON =
            "com.extendedae_plus.util.GuiUtil$1";
    public static final String EXPANDED_AE_MODIFY_PATTERNS_BUTTON =
            "lu.kolja.expandedae.client.gui.widgets.ExpActionButton";
    public static final String EXPANDED_AE_BLOCKING_SETTING = "blocking_type";

    private static final Set<String> HIDDEN_BUTTON_CLASS_NAMES = ConcurrentHashMap.newKeySet();
    private static final Set<String> HIDDEN_SETTING_NAMES = ConcurrentHashMap.newKeySet();

    static {
        registerHiddenButtonClassName(EXTENDED_AE_PLUS_SMART_FEATURE_BUTTON);
        registerHiddenButtonClassName(EXPANDED_AE_MODIFY_PATTERNS_BUTTON);
        registerHiddenSettingName(EXPANDED_AE_BLOCKING_SETTING);
    }

    public static void registerHiddenButtonClassName(String className) {
        if (className == null || className.isBlank()) {
            throw new IllegalArgumentException("className must not be blank");
        }
        HIDDEN_BUTTON_CLASS_NAMES.add(className);
    }

    public static boolean shouldHideToolbarButtonClassName(String className) {
        return HIDDEN_BUTTON_CLASS_NAMES.contains(className);
    }

    public static void registerHiddenSettingName(String settingName) {
        if (settingName == null || settingName.isBlank()) {
            throw new IllegalArgumentException("settingName must not be blank");
        }
        HIDDEN_SETTING_NAMES.add(settingName);
    }

    public static boolean shouldHideToolbarButtonSettingName(String settingName) {
        return HIDDEN_SETTING_NAMES.contains(settingName);
    }

    public static int removeHiddenToolbarButtons(List<?> buttons) {
        int previousSize = buttons.size();
        buttons.removeIf(PatternProviderToolbarButtonHider::shouldHideToolbarButton);
        return previousSize - buttons.size();
    }

    private static boolean shouldHideToolbarButton(Object button) {
        if (button == null) {
            return false;
        }
        if (shouldHideToolbarButtonClassName(button.getClass().getName())) {
            return true;
        }
        if (!(button instanceof SettingToggleButtonAccess accessor)) {
            return false;
        }

        var setting = accessor.ae2lt$getSetting();
        return setting != null && shouldHideToolbarButtonSettingName(setting.getName());
    }

    private PatternProviderToolbarButtonHider() {
    }
}
