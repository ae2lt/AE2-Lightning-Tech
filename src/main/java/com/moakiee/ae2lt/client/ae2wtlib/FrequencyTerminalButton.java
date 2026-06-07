package com.moakiee.ae2lt.client.ae2wtlib;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.client.gui.AEBaseScreen;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.network.OpenFrequencyCardMenuPacket;

/**
 * Injects a small "configure frequency card" button into every ae2wtlib
 * wireless terminal screen. Clicking it opens the card-mode frequency menu for
 * the frequency card installed in that terminal; the server rejects the request
 * (with a message) when no card is installed.
 *
 * <p>Only the menu type's namespace is inspected, so this class does not need to
 * reference ae2wtlib types and is safe to keep registered unconditionally.</p>
 */
@EventBusSubscriber(modid = AE2LightningTech.MODID, value = Dist.CLIENT)
public final class FrequencyTerminalButton {

    private static final int BUTTON_SIZE = 18;

    private FrequencyTerminalButton() {
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!ModList.get().isLoaded("ae2wtlib")) {
            return;
        }
        if (!(event.getScreen() instanceof AEBaseScreen<?> screen)) {
            return;
        }

        var type = screen.getMenu().getType();
        var key = BuiltInRegistries.MENU.getKey(type);
        if (key == null || !key.getNamespace().equals("ae2wtlib")) {
            return;
        }

        int token = screen.getMenu().containerId;
        int x = screen.getGuiLeft();
        int y = Math.max(2, screen.getGuiTop() - BUTTON_SIZE - 2);

        Button button = Button.builder(
                        Component.translatable("ae2lt.gui.button.frequency_card_short"),
                        btn -> PacketDistributor.sendToServer(new OpenFrequencyCardMenuPacket(token)))
                .bounds(x, y, BUTTON_SIZE, BUTTON_SIZE)
                .tooltip(Tooltip.create(Component.translatable("ae2lt.gui.button.open_frequency_card")))
                .build();
        event.addListener(button);
    }
}
