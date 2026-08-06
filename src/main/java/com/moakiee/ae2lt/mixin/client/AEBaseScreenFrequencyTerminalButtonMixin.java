package com.moakiee.ae2lt.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.client.gui.AEBaseScreen;

import com.moakiee.ae2lt.client.ae2wtlib.FrequencyTerminalButton;

/**
 * Adds the ae2wtlib frequency-card button before AE2 populates the toolbar into
 * screen widgets. This matches how native toolbar buttons are created: add them
 * to {@code VerticalButtonBar} before {@code WidgetContainer.populateScreen}.
 */
@Mixin(AEBaseScreen.class)
public abstract class AEBaseScreenFrequencyTerminalButtonMixin {
    @Unique
    private boolean ae2lt$frequencyTerminalButtonAdded;
    @Unique
    private FrequencyTerminalButton.ToolbarButtons ae2lt$frequencyTerminalButtons;

    @Inject(
            method = "init",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/client/gui/WidgetContainer;populateScreen(Ljava/util/function/Consumer;Lnet/minecraft/client/renderer/Rect2i;Lappeng/client/gui/AEBaseScreen;)V"))
    private void ae2lt$addFrequencyTerminalButton(CallbackInfo ci) {
        if (ae2lt$frequencyTerminalButtonAdded) {
            return;
        }

        var screen = (AEBaseScreen<?>) (Object) this;
        if (FrequencyTerminalButton.shouldInject(screen)) {
            ae2lt$frequencyTerminalButtons = FrequencyTerminalButton.addToToolbar(screen);
            ae2lt$frequencyTerminalButtonAdded = true;
        }
    }

    @Inject(method = "updateBeforeRender", at = @At("TAIL"))
    private void ae2lt$updateFrequencyTerminalButtons(CallbackInfo ci) {
        var screen = (AEBaseScreen<?>) (Object) this;
        if (ae2lt$frequencyTerminalButtons != null) {
            ae2lt$frequencyTerminalButtons.update(screen);
        }
    }
}
