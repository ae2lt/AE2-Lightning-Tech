package com.moakiee.ae2lt.client;

import appeng.api.config.Setting;

/**
 * Duck interface added to ExtendedAE Plus setting buttons by our client mixin.
 *
 * <p>This interface deliberately lives outside the mixin package so regular
 * client code never needs to load a mixin class directly.</p>
 */
public interface EapSettingToggleButtonAccess {
    Setting<?> ae2lt$getSetting();
}
