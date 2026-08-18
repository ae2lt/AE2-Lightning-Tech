package com.moakiee.ae2lt.integration.emi;

import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;

/** NeoForge input bridge for interactions omitted from EMI's public recipe-widget API. */
final class EmiMultiblockInputEvents {
    private static boolean registered;

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        MinecraftForge.EVENT_BUS.addListener(EmiMultiblockInputEvents::onMouseDragged);
        MinecraftForge.EVENT_BUS.addListener(EmiMultiblockInputEvents::onMouseScrolled);
    }

    private static void onMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        if (EmiInteractiveMultiblockWidget.routeMouseDragged(
                event.getScreen(),
                event.getMouseX(),
                event.getMouseY(),
                event.getMouseButton(),
                event.getDragX(),
                event.getDragY())) {
            event.setCanceled(true);
        }
    }

    private static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        // 1.20.1 exposes a single scroll delta (vertical); horizontal scroll is
        // not reported separately by the Forge event.
        double delta = event.getScrollDelta();
        if (EmiInteractiveMultiblockWidget.routeMouseScrolled(
                event.getScreen(),
                event.getMouseX(),
                event.getMouseY(),
                0,
                delta)) {
            event.setCanceled(true);
        }
    }

    private EmiMultiblockInputEvents() {
    }
}
