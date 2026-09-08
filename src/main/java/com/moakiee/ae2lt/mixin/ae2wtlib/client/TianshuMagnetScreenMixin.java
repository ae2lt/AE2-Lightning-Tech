package com.moakiee.ae2lt.mixin.ae2wtlib.client;

import appeng.client.gui.Icon;
import appeng.client.gui.WidgetContainer;
import appeng.client.gui.widgets.TabButton;
import appeng.menu.ISubMenu;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moakiee.ae2lt.integration.ae2wtlib.client.TianshuMagnetScreen;
import de.mari_023.ae2wtlib.wct.magnet_card.MagnetScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MagnetScreen.class)
public abstract class TianshuMagnetScreenMixin {
    @WrapOperation(method = "<init>", at = @At(value = "INVOKE",
            target = "Lappeng/client/gui/implementations/AESubScreen;addBackButton(Lappeng/menu/ISubMenu;Ljava/lang/String;Lappeng/client/gui/WidgetContainer;)V"), require = 1)
    private void ae2lt$returnToSameContainer(ISubMenu menu, String id, WidgetContainer widgets, Operation<Void> original) {
        if ((Object) this instanceof TianshuMagnetScreen screen) {
            widgets.add(id, new TabButton(Icon.BACK, menu.getHost().getMainMenuIcon().getHoverName(), b -> screen.returnToTerminal()));
        } else {
            original.call(menu, id, widgets);
        }
    }
}
