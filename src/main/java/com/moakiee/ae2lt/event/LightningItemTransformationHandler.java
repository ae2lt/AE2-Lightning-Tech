package com.moakiee.ae2lt.event;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.lightning.LightningTransformService;
import com.moakiee.ae2lt.lightning.ProtectedItemEntityHelper;
import com.moakiee.ae2lt.logic.research.ResearchRitualService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityInvulnerabilityCheckEvent;
import net.neoforged.neoforge.event.entity.EntityStruckByLightningEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class LightningItemTransformationHandler {
    private static final String TRANSFORMATION_CHECKED_TAG = "ae2lt.lightning_item_transform_checked";

    private LightningItemTransformationHandler() {
    }

    @SubscribeEvent
    public static void onLightningTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LightningBolt lightningBolt)
                || !(lightningBolt.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        var data = lightningBolt.getPersistentData();
        if (data.getBooleanOr(TRANSFORMATION_CHECKED_TAG, false)) {
            return;
        }

        data.putBoolean(TRANSFORMATION_CHECKED_TAG, true);
        ResearchRitualService.handleLightning(serverLevel, lightningBolt);
        LightningTransformService.handleLightning(serverLevel, lightningBolt);
    }

    @SubscribeEvent
    public static void onEntityStruckByLightning(EntityStruckByLightningEvent event) {
        if (event.getEntity() instanceof ItemEntity itemEntity
                && (ProtectedItemEntityHelper.isProtectedItem(itemEntity)
                        || ProtectedItemEntityHelper.isFireproofItem(itemEntity))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityInvulnerabilityCheck(EntityInvulnerabilityCheckEvent event) {
        if (event.getEntity() instanceof ItemEntity itemEntity
                && ProtectedItemEntityHelper.shouldIgnoreDamage(itemEntity, event.getSource())) {
            event.setInvulnerable(true);
        }
    }

    @SubscribeEvent
    public static void onItemTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof ItemEntity itemEntity) {
            ProtectedItemEntityHelper.tick(itemEntity);
        }
    }
}
