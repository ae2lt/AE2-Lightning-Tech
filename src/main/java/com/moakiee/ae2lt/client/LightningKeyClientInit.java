package com.moakiee.ae2lt.client;

import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import appeng.api.client.AEKeyRendering;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.item.ElectroChimeCrystalItem;
import com.moakiee.ae2lt.item.FixedInfiniteCellItem;
import com.moakiee.ae2lt.client.railgun.RailgunClientBootstrap;
import com.moakiee.ae2lt.client.railgun.RailgunClientExtensions;
import com.moakiee.ae2lt.item.railgun.ElectromagneticRailgunItem;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.me.key.LightningKeyType;
import com.moakiee.ae2lt.registry.ModItems;

@EventBusSubscriber(modid = AE2LightningTech.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class LightningKeyClientInit {
    private LightningKeyClientInit() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            RailgunClientBootstrap.install();
            AEKeyRendering.register(LightningKeyType.INSTANCE, LightningKey.class, LightningKeyRenderHandler.INSTANCE);

            ItemProperties.register(
                    ModItems.ELECTRO_CHIME_CRYSTAL.get(),
                    ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "catalysis_stage"),
                    (stack, level, entity, seed) -> ElectroChimeCrystalItem.getCatalysisStage(stack) * 0.25F);

            ItemProperties.register(
                    ModItems.MYSTERIOUS_CELL.get(),
                    ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "cell_type"),
                    (stack, level, entity, seed) -> {
                        if (!FixedInfiniteCellItem.hasType(stack)) {
                            return 0.0F;
                        }
                        return switch (FixedInfiniteCellItem.getType(stack)) {
                            case 1 -> 1.0F;
                            case 2 -> 2.0F;
                            default -> 0.0F;
                        };
                    });

            ItemProperties.register(
                    ModItems.ELECTROMAGNETIC_RAILGUN.get(),
                    ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "ehv_model"),
                    (stack, level, entity, seed) -> entity != null
                            && entity.isUsingItem()
                            && entity.getUseItem() == stack
                            && stack.getItem() instanceof ElectromagneticRailgunItem
                            ? 1.0F : 0.0F);
        });
    }

    @SubscribeEvent
    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(RailgunClientExtensions.INSTANCE, ModItems.ELECTROMAGNETIC_RAILGUN.get());
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiLayersEvent event) {
        event.registerAbove(
                VanillaGuiLayers.ARMOR_LEVEL,
                ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "celestweave_energy_level"),
                CelestweaveArmorEnergyLevel.INSTANCE);
    }

}
