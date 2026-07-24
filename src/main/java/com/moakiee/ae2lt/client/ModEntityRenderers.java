package com.moakiee.ae2lt.client;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.registry.ModEntities;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.TntRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;

@EventBusSubscriber(modid = AE2LightningTech.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ModEntityRenderers {
    private ModEntityRenderers() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.OVERLOAD_TNT.get(), TntRenderer::new);
        event.registerEntityRenderer(ModEntities.FLOATING_MATTER.get(), ItemEntityRenderer::new);
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
        event.registerBlockEntityRenderer(
                ModBlockEntities.PIGMEE_MOLECULAR_ASSEMBLER.get(),
                PigmeeMolecularAssemblerRenderer::new);
    }

    @SubscribeEvent
    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(PigmeeMolecularAssemblerRenderer.LIGHTS_MODEL);
    }

    @SubscribeEvent
    public static void wrapFumoItemModels(ModelEvent.ModifyBakingResult event) {
        wrapFumoItemModel(event, "moakiee_fumo");
        wrapFumoItemModel(event, "cystrysu_fumo");
        wrapFumoItemModel(event, "pigmee_fumo");
    }

    private static void wrapFumoItemModel(ModelEvent.ModifyBakingResult event, String itemId) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, itemId);
        ModelResourceLocation modelId = ModelResourceLocation.inventory(id);
        event.getModels().computeIfPresent(modelId,
                (ignored, model) -> new SpinningFumoBakedModel(model));
    }
}
