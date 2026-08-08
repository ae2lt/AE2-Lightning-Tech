package com.moakiee.ae2lt.client.compat;

import com.moakiee.ae2lt.AE2LightningTech;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Loads the API-backed Polymorph integration only when all of its optional classes are present. */
@Mod.EventBusSubscriber(modid = AE2LightningTech.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TianshuPolymorphCompatBootstrap {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // 1.20.1: the selector uses Polymorph's own widget textures, so PolyEng is no
        // longer required (the 1.21 build used polyeng output/selector sprites).
        if (ModList.get().isLoaded("polymorph")) {
            event.enqueueWork(TianshuPolymorphClientCompat::register);
        }
    }

    private TianshuPolymorphCompatBootstrap() {
    }
}
