package com.moakiee.ae2lt.client;

import com.moakiee.ae2lt.AE2LightningTech;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Inventory Tweaks Refoxed binds its "sort either" action to middle mouse and cancels the
 * pre-screen event for screen classes it does not recognize. Its built-in AE2 exclusions only
 * match AE2 package names, so AE2LT's terminal subclasses are missed. Dispatch the click to the
 * Tianshu screen first and consume the outer event when the screen handled it.
 */
@Mod.EventBusSubscriber(modid = AE2LightningTech.MODID, value = Dist.CLIENT)
public final class TianshuInventoryTweaksCompat {
    private TianshuInventoryTweaksCompat() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE
                && ModList.get().isLoaded("invtweaks")
                && event.getScreen() instanceof TianshuPatternEncodingTermScreen<?> screen) {
            if (screen.mouseClicked(event.getMouseX(), event.getMouseY(), event.getButton())) {
                event.setCanceled(true);
            }
        }
    }
}
