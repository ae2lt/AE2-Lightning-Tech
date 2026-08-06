package com.moakiee.ae2lt.api.frequency;

import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Marker for {@link AbstractContainerMenu} subclasses whose host block entity
 * supports the frequency binding UI. Implementing this lets the screen call
 * {@link FrequencyApi#openBindingScreen(AbstractContainerMenu)} from a button
 * to invoke the shared frequency selection / creation GUI provided by AE2LT.
 *
 * <p>Menus are expected to be AE2 menus opened with a {@code MenuHostLocator}.
 * The server resolves the host from the currently open menu's own locator, so
 * the client does not need to send a separate block position or menu token.</p>
 */
public interface FrequencyBindingMenuHost {
}
