package com.moakiee.ae2lt.client;

import java.util.List;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;

import com.moakiee.ae2lt.client.gui.LightningStatusIconWidget;
import com.moakiee.ae2lt.client.gui.LightningStatusLines;
import com.moakiee.ae2lt.menu.AtmosphericIonizerMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class AtmosphericIonizerScreen extends AEBaseScreen<AtmosphericIonizerMenu> {
    private final AtmosphericIonizerEnergyBar energyBar;

    public AtmosphericIonizerScreen(
            AtmosphericIonizerMenu menu,
            Inventory playerInventory,
            Component title,
            ScreenStyle style) {
        super(menu, playerInventory, title, style);

        addToLeftToolbar(FrequencyBindingClient.createToolbarButton(menu));

        this.energyBar = new AtmosphericIonizerEnergyBar(menu, style.getImage("energyBar"));
        widgets.add("energyBar", energyBar);

        widgets.add("lightningStatus", new LightningStatusIconWidget(() -> List.of(
                LightningStatusLines.title(),
                menu.getStatusMessage(),
                LightningStatusLines.progress(menu.getProgress()),
                menu.getTargetWeatherMessage(),
                menu.getEnergyDemandMessage())));
    }
}
