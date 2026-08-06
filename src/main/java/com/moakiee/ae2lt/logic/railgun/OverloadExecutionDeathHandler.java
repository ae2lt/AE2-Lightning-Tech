package com.moakiee.ae2lt.logic.railgun;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.celestweave.CelestweaveArmorUndyingHandler;

/**
 * Resolves the final cancellation state of an overload-execution death.
 *
 * <p>Celestweave protection has precedence. Otherwise execution clears protection events
 * installed by armor or boss mods, while ordinary deaths retain the result chosen by their
 * existing listeners.
 */
@EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class OverloadExecutionDeathHandler {
    private OverloadExecutionDeathHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!OverloadExecutionContext.contains(event.getEntity())) {
            return;
        }
        if (CelestweaveArmorUndyingHandler.wasProtectedThisTick(event.getEntity())) {
            return;
        }
        event.setCanceled(false);
    }
}
