package com.moakiee.ae2lt.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import com.moakiee.ae2lt.AE2LightningTech;

@EventBusSubscriber(modid = AE2LightningTech.MODID, value = Dist.CLIENT)
public final class ShieldHitFeedbackClientState {
    private static final int SUPPRESSION_TICKS = 20;

    private static int suppressEntityId = -1;
    private static int suppressTicks;

    private ShieldHitFeedbackClientState() {
    }

    public static void suppress(int entityId) {
        suppressEntityId = entityId;
        suppressTicks = SUPPRESSION_TICKS;
        Entity entity = Minecraft.getInstance().level != null
                ? Minecraft.getInstance().level.getEntity(entityId)
                : null;
        if (entity instanceof LivingEntity living) {
            clearFeedback(living);
        }
    }

    public static boolean suppressHurtAnimation(Entity entity) {
        if (!shouldSuppress(entity)) {
            return false;
        }
        if (entity instanceof LivingEntity living) {
            clearFeedback(living);
        }
        return true;
    }

    public static void clearAfterHealthSync(LocalPlayer player) {
        if (!shouldSuppress(player)) {
            return;
        }
        clearFeedback(player);
        suppressTicks = Math.min(suppressTicks, 2);
    }

    private static boolean shouldSuppress(Entity entity) {
        return entity != null
                && suppressTicks > 0
                && entity.getId() == suppressEntityId;
    }

    private static void clearFeedback(LivingEntity entity) {
        entity.hurtTime = 0;
        entity.hurtDuration = 0;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (suppressTicks <= 0) {
            return;
        }
        suppressTicks--;
        if (suppressTicks <= 0) {
            suppressEntityId = -1;
        }
    }
}
