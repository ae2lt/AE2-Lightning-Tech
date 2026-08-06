package com.moakiee.ae2lt.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.widgets.VerticalButtonBar;

/**
 * Exposes the private left-edge {@link VerticalButtonBar} so injected buttons
 * (e.g. the frequency-card button on wireless terminals) can be added to the
 * native toolbar instead of floating over the GUI.
 */
@Mixin(AEBaseScreen.class)
public interface AEBaseScreenAccessor {
    @Accessor("verticalToolbar")
    VerticalButtonBar ae2lt$getVerticalToolbar();
}
