package com.moakiee.ae2lt.client.railgun;

import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

import com.moakiee.ae2lt.AE2LightningTech;

/**
 * Singleton camera shake state used by recoil. Holds an exponentially-decaying
 * intensity and applies a small randomized rotation offset each render frame.
 */
@EventBusSubscriber(modid = AE2LightningTech.MODID, value = Dist.CLIENT)
public final class RailgunCameraShake {

    private static float intensity = 0f;
    private static int decayTicks = 0;

    private RailgunCameraShake() {}

    public static void clear() {
        intensity = 0f;
        decayTicks = 0;
    }

    /** Server-driven recoil entry point. */
    public static void applyRecoil(float pitchUp, int tierOrdinal) {
        LocalPlayer lp = Minecraft.getInstance().player;
        if (lp != null) {
            lp.setXRot(lp.getXRot() - pitchUp);
            lp.xRotO = lp.getXRot();
        }
        // Stack intensity by tier; tier3 = ~0.9 strength.
        intensity = Math.max(intensity, 0.3f + tierOrdinal * 0.2f);
        decayTicks = 6 + tierOrdinal * 2;
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles e) {
        if (intensity <= 0) return;
        // Frame-based decay using partial tick; keeps motion smooth.
        // ThreadLocalRandom avoids the global synchronized monitor inside Math.random().
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        float dx = (float) ((rng.nextDouble() - 0.5) * intensity * 4.0);
        float dy = (float) ((rng.nextDouble() - 0.5) * intensity * 4.0);
        e.setPitch(e.getPitch() + dx);
        e.setYaw(e.getYaw() + dy);
        // Decay
        intensity *= 0.85f;
        if (--decayTicks <= 0) {
            intensity = 0f;
        }
    }
}
