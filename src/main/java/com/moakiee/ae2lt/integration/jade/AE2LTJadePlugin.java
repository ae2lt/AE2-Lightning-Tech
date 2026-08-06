package com.moakiee.ae2lt.integration.jade;

import com.moakiee.ae2lt.blockentity.FirmamentConversionCoreBlockEntity;
import com.moakiee.ae2lt.blockentity.LightningCollectorBlockEntity;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin("ae2lt")
public class AE2LTJadePlugin implements IWailaPlugin {
    // Keep AE2LT-owned Jade providers in this package so addon-specific tooltip
    // code stays separate from AE2's own Jade/WTHIT/TOP abstraction layer.
    private static final LightningCollectorJadeProvider LIGHTNING_COLLECTOR_PROVIDER = new LightningCollectorJadeProvider();
    private static final FirmamentConversionCoreJadeProvider FIRMAMENT_CONVERSION_CORE_PROVIDER =
            new FirmamentConversionCoreJadeProvider();

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(LIGHTNING_COLLECTOR_PROVIDER, LightningCollectorBlockEntity.class);
        registration.registerBlockDataProvider(FIRMAMENT_CONVERSION_CORE_PROVIDER, FirmamentConversionCoreBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(LIGHTNING_COLLECTOR_PROVIDER, Block.class);
        registration.registerBlockComponent(FIRMAMENT_CONVERSION_CORE_PROVIDER, Block.class);
    }
}
