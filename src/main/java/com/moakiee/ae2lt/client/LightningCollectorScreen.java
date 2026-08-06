package com.moakiee.ae2lt.client;

import java.util.List;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ProgressBar;
import appeng.client.gui.widgets.ProgressBar.Direction;

import com.moakiee.ae2lt.client.gui.LightningStatusIconWidget;
import com.moakiee.ae2lt.client.gui.LightningStatusLines;
import com.moakiee.ae2lt.menu.LightningCollectorMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class LightningCollectorScreen extends AEBaseScreen<LightningCollectorMenu> {
    private final ProgressBar progressBar;

    public LightningCollectorScreen(
            LightningCollectorMenu menu,
            Inventory playerInventory,
            Component title,
            ScreenStyle style) {
        super(menu, playerInventory, title, style);
        addToLeftToolbar(FrequencyBindingClient.createToolbarButton(menu));

        this.progressBar = new ProgressBar(menu, style.getImage("progressBar"), Direction.VERTICAL);
        widgets.add("progressBar", this.progressBar);

        widgets.add("lightningStatus", new LightningStatusIconWidget(() -> List.of(
                LightningStatusLines.title(),
                Component.translatable(
                        "gui.ae2lt.lightning_collector.high_output",
                        formatRange(menu.previewHighMin, menu.previewHighMax)),
                Component.translatable(
                        "gui.ae2lt.lightning_collector.extreme_output.simple",
                        formatRange(menu.previewExtremeMin, menu.previewExtremeMax)))));
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        int progress = menu.getCurrentProgress() * 100 / menu.getMaxProgress();
        this.progressBar.setFullMsg(Component.literal(progress + "%"));
    }

    private static String formatRange(int min, int max) {
        return min == max ? Integer.toString(min) : min + "~" + max;
    }
}
