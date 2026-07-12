package com.moakiee.ae2lt.mixin.client;

import appeng.api.config.Setting;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "com.extendedae_plus.client.gui.widgets.EAPSettingToggleButton", remap = false)
public interface EapSettingToggleButtonAccessor {

    @Accessor("buttonSetting")
    Setting<?> ae2lt$getSetting();
}
