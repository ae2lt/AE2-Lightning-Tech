package com.moakiee.ae2lt.client;

import appeng.client.gui.me.common.TerminalSettingsScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.widgets.IconButton;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import com.moakiee.ae2lt.mixin.client.AEBaseScreenAccessor;
import com.moakiee.ae2lt.mixin.client.VerticalButtonBarAccessor;
import com.moakiee.ae2lt.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.ScreenEvent;

import java.util.List;

@Mod.EventBusSubscriber(modid = AE2LightningTech.MODID, value = Dist.CLIENT)
public final class TianshuTerminalSettingsHook {
    private TianshuTerminalSettingsHook() {
    }

    @SubscribeEvent
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void addTianshuSettingsTab(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof TerminalSettingsScreen settings)
                || !(settings.getMenu() instanceof TianshuPatternEncodingTermMenu)) return;

        // AE2's help button is owned by the native left toolbar. Add this button to
        // the same toolbar so its width, right edge, spacing, background, and resize
        // behavior all stay consistent with the rest of the terminal UI.
        var toolbar = ((AEBaseScreenAccessor) settings).ae2lt$getVerticalToolbar();
        var buttons = ((VerticalButtonBarAccessor) toolbar).ae2lt$getButtons();
        for (var existing : buttons) {
            if (existing instanceof TianshuSettingsButton) return;
        }

        var button = new TianshuSettingsButton(ignored ->
                ((AEBaseScreenAccessor) settings).ae2lt$switchToScreen(
                        new TianshuTerminalSettingsScreen(settings)));
        toolbar.add(button);
        // Init.Post runs after AE2 populated its toolbar. Register the new button
        // for this initialization; later re-initializations populate it normally.
        event.addListener(button);
    }

    private static final class TianshuSettingsButton extends IconButton {
        private TianshuSettingsButton(OnPress onPress) {
            super(onPress);
        }

        @Override
        protected Icon getIcon() {
            return null;
        }

        @Override
        protected Item getItemOverlay() {
            return ModItems.TIANSHU_PATTERN_ENCODING_TERMINAL.get();
        }

        @Override
        public List<Component> getTooltipMessage() {
            return List.of(Component.translatable("ae2lt.tianshu.settings.title"));
        }
    }
}
