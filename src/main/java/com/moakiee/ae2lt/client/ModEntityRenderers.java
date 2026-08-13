package com.moakiee.ae2lt.client;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.client.core.MatrixCoreEffectRenderer;
import com.moakiee.ae2lt.client.core.TianshuCoreEffectRenderer;
import com.moakiee.ae2lt.registry.ModEntities;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.TntRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;

import com.moakiee.ae2lt.registry.ModBlocks;

@Mod.EventBusSubscriber(modid = AE2LightningTech.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ModEntityRenderers {
    private ModEntityRenderers() {
    }

    @SubscribeEvent
    public static void registerRenderLayers(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemBlockRenderTypes.setRenderLayer(
                ModBlocks.PIGMEE_MOLECULAR_ASSEMBLER.get(),
                RenderType.cutout()));
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.OVERLOAD_TNT.get(), TntRenderer::new);
        event.registerEntityRenderer(ModEntities.FLOATING_MATTER.get(), ItemEntityRenderer::new);
        event.registerEntityRenderer(
                ModEntities.RITUAL_HYPERDIMENSIONAL_PIGMEE.get(),
                RitualHyperdimensionalPigmeeRenderer::new);
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
                ModBlockEntities.OVERLOADED_PATTERN_PROVIDER.get(),
                WirelessConnectorHostRenderer::new);
        event.registerBlockEntityRenderer(
                ModBlockEntities.EXTENDED_OVERLOADED_PATTERN_PROVIDER.get(),
                WirelessConnectorHostRenderer::new);
        event.registerBlockEntityRenderer(
                ModBlockEntities.OVERLOADED_INTERFACE.get(),
                WirelessConnectorHostRenderer::new);
        if (ModBlockEntities.OVERLOADED_POWER_SUPPLY != null) {
            event.registerBlockEntityRenderer(
                    ModBlockEntities.OVERLOADED_POWER_SUPPLY.get(),
                    WirelessConnectorHostRenderer::new);
        }
        event.registerBlockEntityRenderer(
                ModBlockEntities.FUMO.get(),
                FumoBlockRenderer::new);
        event.registerBlockEntityRenderer(
                ModBlockEntities.PIGMEE_MOLECULAR_ASSEMBLER.get(),
                PigmeeMolecularAssemblerRenderer::new);
        event.registerBlockEntityRenderer(
                ModBlockEntities.MATRIX_CONTROLLER.get(),
                MatrixCoreEffectRenderer::new);
        event.registerBlockEntityRenderer(
                ModBlockEntities.TIANSHU_SUPERCOMPUTER_CONTROLLER.get(),
                TianshuCoreEffectRenderer::new);
    }

    @SubscribeEvent
    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(PigmeeMolecularAssemblerRenderer.LIGHTS_MODEL);
        event.register(HyperdimensionalPigmeeTextureLayer.MODEL);
    }

    @SubscribeEvent
    public static void wrapFumoItemModels(ModelEvent.ModifyBakingResult event) {
        wrapFumoItemModel(event, "moakiee_fumo");
        wrapFumoItemModel(event, "cystrysu_fumo");
        wrapFumoItemModel(event, "pigmee_fumo");
        wrapFumoItemModel(event, "creative_pigmee_fumo");
        wrapFumoItemModel(event, "hyperdimensional_pigmee_fumo");
    }

    private static void wrapFumoItemModel(ModelEvent.ModifyBakingResult event, String itemId) {
        ResourceLocation id = new ResourceLocation(AE2LightningTech.MODID, itemId);
        ModelResourceLocation modelId = new ModelResourceLocation(id, "inventory");
        event.getModels().computeIfPresent(modelId, (ignored, model) ->
                itemId.equals("hyperdimensional_pigmee_fumo")
                        ? new HyperdimensionalPigmeeBakedModel(model)
                        : new SpinningFumoBakedModel(model));
    }
}
