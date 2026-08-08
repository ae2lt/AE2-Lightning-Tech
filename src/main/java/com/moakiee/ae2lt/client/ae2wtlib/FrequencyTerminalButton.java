package com.moakiee.ae2lt.client.ae2wtlib;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import appeng.client.gui.AEBaseScreen;
import appeng.menu.SlotSemantics;

import com.moakiee.ae2lt.client.FrequencyBindingClient;
import com.moakiee.ae2lt.client.TextureToggleButton;
import com.moakiee.ae2lt.item.OverloadedFrequencyCardItem;
import com.moakiee.ae2lt.mixin.client.AEBaseScreenAccessor;

import de.mari_023.ae2wtlib.wut.IUniversalTerminalCapable;

/**
 * Adds a "configure frequency card" button to ae2wtlib wireless terminal
 * screens. Called from an {@link AEBaseScreen} init mixin before AE2 populates
 * the screen widgets, so the button is registered through the native toolbar
 * path instead of being appended after init events.
 *
 * <p>The button is appended to the bottom of AE2's native left vertical toolbar
 * (the terminal already stacks its own buttons from the top), styled like the
 * toolbar frequency button on the mod's machines instead of floating over the
 * GUI. AE2WTLib's screen capability marks both native and externally registered
 * wireless terminals, including universal-terminal variants.</p>
 */
public final class FrequencyTerminalButton {

    private FrequencyTerminalButton() {
    }

    public static boolean shouldInject(AEBaseScreen<?> screen) {
        if (!ModList.get().isLoaded("ae2wtlib")) {
            return false;
        }

        return screen instanceof IUniversalTerminalCapable;
    }

    public static ToolbarButtons addToToolbar(AEBaseScreen<?> screen) {
        // Append to the native left toolbar. VerticalButtonBar lays out its button
        // list top-to-bottom every frame, so add() == bottom of the column.
        // AEBaseScreen.init() will populate the toolbar into renderables after
        // this hook runs.
        var toolbar = ((AEBaseScreenAccessor) screen).ae2lt$getVerticalToolbar();
        var buttons = new ToolbarButtons(
                FrequencyBindingClient.createCardToolbarButton(),
                FrequencyBindingClient.createCardAutoConnectToolbarButton());
        toolbar.add(buttons.configureButton());
        toolbar.add(buttons.autoConnectButton());
        buttons.update(screen);
        return buttons;
    }

    private static ItemStack findInstalledFrequencyCard(AEBaseScreen<?> screen) {
        for (var slot : screen.getMenu().getSlots(SlotSemantics.UPGRADE)) {
            var stack = slot.getItem();
            if (stack.getItem() instanceof OverloadedFrequencyCardItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    public record ToolbarButtons(TextureToggleButton configureButton, TextureToggleButton autoConnectButton) {
        public void update(AEBaseScreen<?> screen) {
            var card = findInstalledFrequencyCard(screen);
            boolean hasCard = !card.isEmpty();
            configureButton.setVisibility(hasCard);
            autoConnectButton.setVisibility(hasCard);
            if (hasCard) {
                autoConnectButton.setState(OverloadedFrequencyCardItem.getData(card).autoConnect());
            }
        }
    }
}
