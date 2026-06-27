package com.moakiee.ae2lt.device.capability;

import java.util.ArrayList;
import java.util.List;

import com.moakiee.ae2lt.device.module.DeviceModuleHost;
import com.moakiee.ae2lt.device.module.OverloadDeviceModuleItem;

/**
 * Aggregates capabilities declared by all installed modules of a device.
 *
 * <p>Services iterate the returned list once and pattern-match on the variants
 * they recognize; everything else is silently ignored. This is what lets a
 * shared capability like {@link DeviceCapability.AccelerationFactor} mean
 * different things to different devices.
 */
public final class CapabilityResolver {

    private CapabilityResolver() {}

    public static List<DeviceCapability> collect(DeviceModuleHost host) {
        List<DeviceCapability> out = new ArrayList<>();
        host.installedModuleStacks().forEach(stack -> {
            if (stack.getItem() instanceof OverloadDeviceModuleItem module) {
                out.addAll(module.capabilities(stack));
            }
        });
        return out;
    }
}
