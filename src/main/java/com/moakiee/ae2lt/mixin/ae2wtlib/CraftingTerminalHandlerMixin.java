package com.moakiee.ae2lt.mixin.ae2wtlib;

import appeng.menu.locator.ItemMenuHostLocator;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.moakiee.ae2lt.integration.ae2wtlib.TianshuCraftingLocatorScope;
import de.mari_023.ae2wtlib.wct.CraftingTerminalHandler;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = CraftingTerminalHandler.class, remap = false)
public abstract class CraftingTerminalHandlerMixin {
    @WrapMethod(method = "getLocator")
    private ItemMenuHostLocator ae2lt$recognizeCraftingTerminal(Operation<ItemMenuHostLocator> original) {
        boolean previous = TianshuCraftingLocatorScope.enter();
        try { return original.call(); }
        finally { TianshuCraftingLocatorScope.restore(previous); }
    }
}
