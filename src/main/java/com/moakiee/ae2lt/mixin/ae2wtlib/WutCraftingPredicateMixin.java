package com.moakiee.ae2lt.mixin.ae2wtlib;

import com.moakiee.ae2lt.integration.ae2wtlib.TianshuCraftingLocatorScope;
import com.moakiee.ae2lt.integration.ae2wtlib.TianshuWctIntegration;
import de.mari_023.ae2wtlib.api.registration.WTDefinition;
import de.mari_023.ae2wtlib.api.terminal.WUTHandler;
import de.mari_023.ae2wtlib.wut.WTDefinitions;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = WUTHandler.class, remap = false)
public abstract class WutCraftingPredicateMixin {
    @Inject(method = "hasTerminal", at = @At("RETURN"), cancellable = true, require = 1)
    private static void ae2lt$craftingEnhancementSource(ItemStack stack, WTDefinition terminal, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() && TianshuCraftingLocatorScope.active() && terminal == WTDefinitions.CRAFTING
                && TianshuWctIntegration.hasTianshuCrafting(stack)) cir.setReturnValue(true);
    }
}
