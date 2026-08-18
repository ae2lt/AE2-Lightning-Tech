package com.moakiee.ae2lt.client;

import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.common.Mod;

import appeng.api.client.AEKeyRendering;
import appeng.items.storage.BasicStorageCell;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.item.ElectroChimeCrystalItem;
import com.moakiee.ae2lt.item.FixedInfiniteCellItem;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.me.key.LightningKeyType;
import com.moakiee.ae2lt.registry.ModItems;

@Mod.EventBusSubscriber(modid = AE2LightningTech.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class LightningKeyClientInit {
    private LightningKeyClientInit() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            AEKeyRendering.register(LightningKeyType.INSTANCE, LightningKey.class, LightningKeyRenderHandler.INSTANCE);

            ItemProperties.register(
                    ModItems.ELECTRO_CHIME_CRYSTAL.get(),
                    new ResourceLocation(AE2LightningTech.MODID, "catalysis_stage"),
                    (stack, level, entity, seed) -> ElectroChimeCrystalItem.getCatalysisStage(stack) * 0.25F);

            ItemProperties.register(
                    ModItems.MYSTERIOUS_CELL.get(),
                    new ResourceLocation(AE2LightningTech.MODID, "cell_type"),
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
        });
    }

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register(
                (stack, tintIndex) -> 0xFF000000 | BasicStorageCell.getColor(stack, tintIndex),
                ModItems.LIGHTNING_STORAGE_COMPONENT_I.get(),
                ModItems.LIGHTNING_STORAGE_COMPONENT_II.get(),
                ModItems.LIGHTNING_STORAGE_COMPONENT_III.get(),
                ModItems.LIGHTNING_STORAGE_COMPONENT_IV.get(),
                ModItems.LIGHTNING_STORAGE_COMPONENT_V.get());
    }
}

