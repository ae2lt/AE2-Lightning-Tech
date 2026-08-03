package com.moakiee.ae2lt.integration.emi;

import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

/** NeoForge input bridge for interactions omitted from EMI's public recipe-widget API. */
final class EmiMultiblockInputEvents {
    private static boolean registered;

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        NeoForge.EVENT_BUS.addListener(EmiMultiblockInputEvents::onMouseDragged);
        NeoForge.EVENT_BUS.addListener(EmiMultiblockInputEvents::onMouseScrolled);
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
        if (EmiInteractiveMultiblockWidget.routeMouseScrolled(
                event.getScreen(),
                event.getMouseX(),
                event.getMouseY(),
                event.getScrollDeltaX(),
                event.getScrollDeltaY())) {
            event.setCanceled(true);
        }
    }

    private EmiMultiblockInputEvents() {
    }
}
