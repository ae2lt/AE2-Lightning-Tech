package com.moakiee.ae2lt.client;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.registry.ModEntities;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import net.minecraft.client.renderer.entity.TntRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.EventBusSubscriber;
import net.minecraftforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = AE2LightningTech.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ModEntityRenderers {
    private ModEntityRenderers() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.OVERLOAD_TNT.get(), TntRenderer::new);
        event.registerBlockEntityRenderer(
                ModBlockEntities.LIGHTNING_SIMULATION_CHAMBER.get(),
                LightningSimulationChamberRenderer::new);
        event.registerBlockEntityRenderer(
                ModBlockEntities.LIGHTNING_ASSEMBLY_CHAMBER.get(),
                LightningAssemblyChamberRenderer::new);
        event.registerBlockEntityRenderer(
                ModBlockEntities.CRYSTAL_CATALYZER.get(),
                CrystalCatalyzerRenderer::new);
        event.registerBlockEntityRenderer(
                ModBlockEntities.FUMO.get(),
                FumoBlockRenderer::new);
    }
}

