package com.moakiee.ae2lt.mixin.ae2wtlib;

import de.mari_023.ae2wtlib.wct.CraftingTerminalHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = CraftingTerminalHandler.class, remap = false)
public interface CraftingTerminalHandlerAccessor {
    @Invoker("invalidateCache") void ae2lt$invalidateCache();
}
