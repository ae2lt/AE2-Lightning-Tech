package com.moakiee.ae2lt.client;
import com.moakiee.ae2lt.network.NetworkInit;

import java.util.List;

import com.moakiee.ae2lt.menu.FrequencyBindingMenu;
import com.moakiee.ae2lt.network.OpenFrequencyMenuPacket;
import com.moakiee.ae2lt.network.ToggleFrequencyCardAutoConnectPacket;
import net.minecraft.network.chat.Component;

public final class FrequencyBindingClient {
    private FrequencyBindingClient() {
    }

    public static TextureToggleButton createToolbarButton(FrequencyBindingMenu menu) {
        var button = new TextureToggleButton(
                TextureToggleButton.ButtonType.FREQUENCY_BIND,
                ignored -> NetworkInit.sendToServer(OpenFrequencyMenuPacket.forBlock()));
        button.setTooltipAt(0, List.of(Component.translatable("ae2lt.gui.frequency.bind")));
        return button;
    }

    public static TextureToggleButton createCardToolbarButton() {
        var button = new TextureToggleButton(
                TextureToggleButton.ButtonType.FREQUENCY_BIND,
                ignored -> NetworkInit.sendToServer(OpenFrequencyMenuPacket.forCard()));
        button.setTooltipAt(0, List.of(Component.translatable("ae2lt.gui.button.open_frequency_card")));
        return button;
    }

    public static TextureToggleButton createCardAutoConnectToolbarButton() {
        var button = new TextureToggleButton(
                TextureToggleButton.ButtonType.MODE,
                ignored -> NetworkInit.sendToServer(ToggleFrequencyCardAutoConnectPacket.forTerminalCard()));
        button.setTooltipOff(List.of(Component.translatable("ae2lt.gui.button.auto_connect_off")));
        button.setTooltipOn(List.of(Component.translatable("ae2lt.gui.button.auto_connect_on")));
        return button;
    }
}
