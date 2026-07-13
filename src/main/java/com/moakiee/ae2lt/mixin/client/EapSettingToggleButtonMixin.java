package com.moakiee.ae2lt.mixin.client;

import appeng.api.config.Setting;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

import com.moakiee.ae2lt.client.EapSettingToggleButtonAccess;

@Pseudo
@Mixin(targets = "com.extendedae_plus.client.gui.widgets.EAPSettingToggleButton", remap = false)
public abstract class EapSettingToggleButtonMixin implements EapSettingToggleButtonAccess {

    @Shadow(remap = false)
    @Final
    private Setting<?> buttonSetting;

    @Override
    public Setting<?> ae2lt$getSetting() {
        return this.buttonSetting;
    }
}
