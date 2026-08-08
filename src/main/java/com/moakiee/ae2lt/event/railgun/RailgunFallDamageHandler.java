package com.moakiee.ae2lt.event.railgun;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.living.LivingFallEvent;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.logic.railgun.RailgunRecoilService;

/**
 * Cancels fall damage while the recoil grace tag is active. Without this,
 * mid-air max-tier shots would kill the player from the recoil-induced
 * fall.
 */
@EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class RailgunFallDamageHandler {

    private RailgunFallDamageHandler() {}

    @SubscribeEvent
    public static void onFall(LivingFallEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        if (RailgunRecoilService.inRecoilGrace(p)) {
            e.setCanceled(true);
        }
    }
}
