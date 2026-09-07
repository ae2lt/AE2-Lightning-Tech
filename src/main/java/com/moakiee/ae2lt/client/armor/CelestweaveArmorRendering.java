package com.moakiee.ae2lt.client.armor;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.registry.ModItems;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@EventBusSubscriber(modid = AE2LightningTech.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CelestweaveArmorRendering {
    private CelestweaveArmorRendering() {}

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        for (EquipmentSlot slot : CelestweaveArmorLayer.SLOTS) {
            for (boolean slim : new boolean[] {false, true}) {
                event.registerLayerDefinition(CelestweaveArmorModel.layer(slot, slim),
                        () -> CelestweaveArmorGeometry.create(slot, slim));
            }
        }
    }

    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (var skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer != null) {
                renderer.addLayer(new CelestweaveArmorLayer<>(renderer, event.getEntityModels(),
                        skin == PlayerSkin.Model.SLIM));
            }
        }
        for (var type : event.getEntityTypes()) {
            if (event.getRenderer(type) instanceof LivingEntityRenderer<?, ?> renderer
                    && renderer.getModel() instanceof HumanoidModel<?>) {
                addHumanoidLayer(renderer, event.getEntityModels());
            }
        }
    }

    // The runtime model check above establishes the type relation erased by the renderer registry.
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addHumanoidLayer(LivingEntityRenderer renderer, EntityModelSet models) {
        renderer.addLayer(new CelestweaveArmorLayer(renderer, models, false));
    }

    @SubscribeEvent
    public static void registerExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            // Empty geometry also suppresses vanilla trims/glint on the old enclosing armor shell.
            // The procedural field is rendered by CelestweaveArmorLayer.
            private final Model empty = new HumanoidModel<>(
                    CelestweaveArmorGeometry.create(EquipmentSlot.MAINHAND, false).bakeRoot());

            @Override
            public Model getGenericArmorModel(LivingEntity entity, ItemStack stack,
                    EquipmentSlot slot, HumanoidModel<?> original) {
                return empty;
            }
        }, ModItems.CELESTWEAVE_OCULUS, ModItems.CELESTWEAVE_CORE,
                ModItems.CELESTWEAVE_CONDUIT, ModItems.CELESTWEAVE_STRIDE,
                ModItems.PHASE_LOCK_PROJECTION_HEAD, ModItems.PHASE_LOCK_PROJECTION,
                ModItems.PHASE_LOCK_PROJECTION_LEGS, ModItems.PHASE_LOCK_PROJECTION_FEET);
    }
}
