package com.moakiee.ae2lt.item;

import java.util.List;

import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.overload.armor.ArmorOverloadRules;
import com.moakiee.ae2lt.overload.armor.ArmorPart;
import com.moakiee.ae2lt.overload.armor.module.AutoFeedSubmodule;

public final class AutoFeedSubmoduleItem extends AbstractSingleArmorSubmoduleItem {

    public AutoFeedSubmoduleItem(Properties properties) {
        super(
                properties.stacksTo(1),
                ArmorPart.HEAD,
                AutoFeedSubmodule.INSTANCE,
                "item.ae2lt.module_auto_feed.tooltip",
                stack -> List.of(
                        new DeviceCapability.AutoFeed(AE2LTCommonConfig.overloadArmorAutoFeedThreshold()),
                        new DeviceCapability.PassiveDrain(ArmorOverloadRules.AUTO_FEED_PASSIVE_DRAIN_FE)));
    }
}
