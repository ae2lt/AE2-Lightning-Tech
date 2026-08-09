package com.moakiee.ae2lt.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.widgets.VerticalButtonBar;

/**
 * Exposes the private left-edge {@link VerticalButtonBar} so injected buttons
 * (e.g. the frequency-card button on wireless terminals) can be added to the
 * native toolbar instead of floating over the GUI.
 */
@Mixin(value = AEBaseScreen.class, remap = false)
public interface AEBaseScreenAccessor {
    @Accessor("verticalToolbar")
    VerticalButtonBar ae2lt$getVerticalToolbar();

    @Invoker("switchToScreen")
    void ae2lt$switchToScreen(AEBaseScreen<?> screen);
}
