package com.moakiee.ae2lt;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

/**
 * AE2 Lightning Tech — Forge 1.20.1 port entry point.
 * Milestone A: minimal skeleton shell; subsystems will be ported in later milestones.
 */
@Mod(AE2LightningTech.MODID)
public class AE2LightningTech {
    public static final String MODID = "ae2lt";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AE2LightningTech() {
        IEventBus modEventBus = net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("AE2 Lightning Tech (Forge 1.20.1 port) skeleton initialized.");
    }
}
