package com.moakiee.ae2lt.client;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderBlockScreenEffectEvent;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.celestweave.service.ArmorCapabilityCollector;
import com.moakiee.ae2lt.device.capability.DeviceCapability;

/** Hides vanilla fire visuals while an equipped Celestweave shield is active. */
@EventBusSubscriber(modid = AE2LightningTech.MODID, value = Dist.CLIENT)
public final class CelestweaveShieldFireVisuals {
    private CelestweaveShieldFireVisuals() {
    }

    @SubscribeEvent
    public static void onRenderBlockScreenEffect(RenderBlockScreenEffectEvent event) {
        if (event.getOverlayType() == RenderBlockScreenEffectEvent.OverlayType.FIRE
                && shouldHideFire(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    public static boolean shouldHideFire(Entity entity) {
        if (!(entity instanceof Player player)) {
            return false;
        }
        return ArmorCapabilityCollector.collectPerInstalledStack(player).stream()
                .anyMatch(active -> active.capability() instanceof DeviceCapability.StagedMitigation);
    }
}
