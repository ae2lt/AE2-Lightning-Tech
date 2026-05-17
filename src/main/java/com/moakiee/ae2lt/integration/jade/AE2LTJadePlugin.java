package com.moakiee.ae2lt.integration.jade;

import com.moakiee.ae2lt.blockentity.LightningCollectorBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;

import net.minecraft.world.level.block.Block;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.view.HideThingsExtensionProvider;

@WailaPlugin("ae2lt")
public class AE2LTJadePlugin implements IWailaPlugin {
    // Keep AE2LT-owned Jade providers in this package so addon-specific tooltip
    // code stays separate from AE2's own Jade/WTHIT/TOP abstraction layer.

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(LightningCollectorJadeProvider.INSTANCE, LightningCollectorBlockEntity.class);
        registration.registerFluidStorage(
                HideThingsExtensionProvider.instance(),
                OverloadedPatternProviderBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(LightningCollectorJadeProvider.Client.INSTANCE, Block.class);
    }
}
