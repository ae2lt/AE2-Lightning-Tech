package com.moakiee.ae2lt.client.ae2wtlib;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import appeng.client.gui.AEBaseScreen;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.AppEngSlot;

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
        var buttons = new ToolbarButtons(screen);
        toolbar.add(buttons.configureButton());
        toolbar.add(buttons.autoConnectButton());
        buttons.update(screen);
        return buttons;
    }

    private static ItemStack findInstalledFrequencyCard(AEBaseScreen<?> screen) {
        for (var slot : screen.getMenu().getSlots(SlotSemantics.UPGRADE)) {
            // AE2WTLib disables upgrade menu slots outside the scrolling panel's
            // visible window. AppEngSlot#getItem then reports EMPTY even though
            // the backing upgrade inventory still contains the card, so inspect
            // the slot inventory rather than its presentation state.
            if (!(slot instanceof AppEngSlot appEngSlot)) {
                continue;
            }
            var stack = appEngSlot.getSlotInv().getStackInSlot(0);
            if (stack.getItem() instanceof OverloadedFrequencyCardItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    public static final class ToolbarButtons {
        private static final int SYNC_GRACE_TICKS = 40;

        private final TextureToggleButton configureButton;
        private final TextureToggleButton autoConnectButton;
        private Boolean pendingAutoConnect;
        private int pendingUntilTick;

        private ToolbarButtons(AEBaseScreen<?> screen) {
            this.configureButton = FrequencyBindingClient.createCardToolbarButton();
            this.autoConnectButton = FrequencyBindingClient.createCardAutoConnectToolbarButton(
                    this::toggleAutoConnectOptimistically);
            update(screen);
        }

        public TextureToggleButton configureButton() {
            return configureButton;
        }

        public TextureToggleButton autoConnectButton() {
            return autoConnectButton;
        }

        private void toggleAutoConnectOptimistically(int previousState) {
            pendingAutoConnect = previousState == 0;
            pendingUntilTick = Minecraft.getInstance().player == null
                    ? 0
                    : Minecraft.getInstance().player.tickCount + SYNC_GRACE_TICKS;
            autoConnectButton.setState(pendingAutoConnect);
        }

        public void update(AEBaseScreen<?> screen) {
            var card = findInstalledFrequencyCard(screen);
            boolean hasCard = !card.isEmpty();
            configureButton.setVisibility(hasCard);
            autoConnectButton.setVisibility(hasCard);
            if (hasCard) {
                boolean observed = OverloadedFrequencyCardItem.getData(card).autoConnect();
                var player = Minecraft.getInstance().player;
                if (pendingAutoConnect != null && observed == pendingAutoConnect) {
                    pendingAutoConnect = null;
                } else if (pendingAutoConnect != null
                        && player != null
                        && player.tickCount <= pendingUntilTick) {
                    autoConnectButton.setState(pendingAutoConnect);
                    return;
                } else {
                    pendingAutoConnect = null;
                }
                autoConnectButton.setState(observed);
            } else {
                pendingAutoConnect = null;
            }
        }
    }
}
