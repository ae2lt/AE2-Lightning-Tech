package com.moakiee.ae2lt.client;

import appeng.client.gui.me.common.TerminalSettingsScreen;
import appeng.client.gui.widgets.TabButton;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import com.moakiee.ae2lt.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = AE2LightningTech.MODID, value = Dist.CLIENT)
public final class TianshuTerminalSettingsHook {
    private TianshuTerminalSettingsHook() {
    }

    @SubscribeEvent
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void addTianshuSettingsTab(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof TerminalSettingsScreen settings)
                || !(settings.getMenu() instanceof TianshuPatternEncodingTermMenu)) return;
        var button = new TabButton(ModItems.TIANSHU_PATTERN_ENCODING_TERMINAL.get().getDefaultInstance(),
                Component.translatable("ae2lt.tianshu.settings.title"), ignored ->
                settings.switchToScreen(new TianshuTerminalSettingsScreen(settings)));
        button.setX(settings.getGuiLeft() - 22);
        button.setY(settings.getGuiTop() + 20);
        event.addListener(button);
    }
}
