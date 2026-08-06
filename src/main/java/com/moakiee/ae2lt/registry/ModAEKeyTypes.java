package com.moakiee.ae2lt.registry;

import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.AEKeyTypesInternal;
import net.minecraftforge.registries.RegisterEvent;

import com.moakiee.ae2lt.me.key.LightningKeyType;

public final class ModAEKeyTypes {
    private ModAEKeyTypes() {
    }

    public static void register(RegisterEvent event) {
        if (AEKeyTypesInternal.getRegistry() != null
                && java.util.Objects.equals(event.getForgeRegistry(), AEKeyTypesInternal.getRegistry())) {
            AEKeyTypes.register(LightningKeyType.INSTANCE);
        }
    }
}

