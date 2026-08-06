package com.moakiee.ae2lt.registry;

import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import net.neoforged.neoforge.registries.RegisterEvent;

import com.moakiee.ae2lt.me.key.LightningKeyType;

public final class ModAEKeyTypes {
    private ModAEKeyTypes() {
    }

    public static void register(RegisterEvent event) {
        if (event.getRegistryKey().equals(AEKeyType.REGISTRY_KEY)) {
            AEKeyTypes.register(LightningKeyType.INSTANCE);
        }
    }
}
