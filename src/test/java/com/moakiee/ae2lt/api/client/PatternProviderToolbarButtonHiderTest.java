package com.moakiee.ae2lt.api.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import appeng.api.config.Setting;
import appeng.api.config.YesNo;

import com.moakiee.ae2lt.client.SettingToggleButtonAccess;

class PatternProviderToolbarButtonHiderTest {
    @Test
    void hidesAllExtendedAePlusServerSettingButtons() {
        assertTrue(PatternProviderToolbarButtonHider.shouldHideToolbarButtonClassName(
                PatternProviderToolbarButtonHider.EXTENDED_AE_PLUS_SERVER_SETTING_BUTTON));
    }

    @Test
    void hidesExpandedAePatternModificationAndBlockingButtons() {
        assertTrue(PatternProviderToolbarButtonHider.shouldHideToolbarButtonClassName(
                PatternProviderToolbarButtonHider.EXPANDED_AE_MODIFY_PATTERNS_BUTTON));
        assertTrue(PatternProviderToolbarButtonHider.shouldHideToolbarButtonSettingName(
                PatternProviderToolbarButtonHider.EXPANDED_AE_BLOCKING_SETTING));
    }

    @Test
    void removesRegisteredButtonTypesWithoutTouchingOtherButtons() {
        PatternProviderToolbarButtonHider.registerHiddenButtonClassName(HiddenButton.class.getName());
        var visibleButton = new Object();
        var buttons = new ArrayList<>();
        buttons.add(visibleButton);
        buttons.add(new HiddenButton());

        assertEquals(1, PatternProviderToolbarButtonHider.removeHiddenToolbarButtons(buttons));
        assertEquals(1, buttons.size());
        assertEquals(visibleButton, buttons.getFirst());
    }

    @Test
    void removesButtonsBoundToRegisteredSettings() {
        var buttons = new ArrayList<>();
        buttons.add(new SettingButton(
                new Setting<>(PatternProviderToolbarButtonHider.EXPANDED_AE_BLOCKING_SETTING, YesNo.class)));

        assertEquals(1, PatternProviderToolbarButtonHider.removeHiddenToolbarButtons(buttons));
        assertTrue(buttons.isEmpty());
    }

    private static final class HiddenButton {
    }

    private record SettingButton(Setting<?> setting) implements SettingToggleButtonAccess {
        @Override
        public Setting<?> ae2lt$getSetting() {
            return setting;
        }
    }
}
