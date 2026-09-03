package com.moakiee.ae2lt.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import appeng.client.gui.AESubScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.style.StyleManager;

import com.moakiee.ae2lt.client.gui.AE2LTStyleManager;

@Mixin(value = AESubScreen.class, remap = false)
public abstract class AESubScreenStyleMixin {
    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/client/gui/style/StyleManager;loadStyleDoc(Ljava/lang/String;)Lappeng/client/gui/style/ScreenStyle;"))
    private ScreenStyle ae2lt$loadNamespacedStyle(String path) {
        if (AE2LTStyleManager.handles(path)) {
            return AE2LTStyleManager.loadStyleDoc(path);
        }
        return StyleManager.loadStyleDoc(path);
    }
}
