package com.moakiee.ae2lt.client.compat;

import com.moakiee.ae2lt.AE2LightningTech;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** Loads the API-backed Polymorph integration only when all of its optional classes are present. */
@EventBusSubscriber(modid = AE2LightningTech.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TianshuPolymorphCompatBootstrap {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        if (ModList.get().isLoaded("polymorph") && ModList.get().isLoaded("polyeng")) {
            event.enqueueWork(TianshuPolymorphClientCompat::register);
        }
    }

    private TianshuPolymorphCompatBootstrap() {
    }
}
