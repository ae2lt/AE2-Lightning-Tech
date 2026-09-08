package com.moakiee.ae2lt.mixin.ae2wtlib;

import com.moakiee.ae2lt.integration.ae2wtlib.TianshuWctIntegration;
import de.mari_023.ae2wtlib.wut.recipe.Combine;
import de.mari_023.ae2wtlib.wut.recipe.Upgrade;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = {Combine.class, Upgrade.class}, remap = false)
public abstract class TianshuTerminalRecipeMixin {
    @Inject(method = "matches(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/world/level/Level;)Z", at = @At("RETURN"), cancellable = true, require = 1)
    private void ae2lt$rejectConflictingInputs(CraftingInput input, Level level, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;
        for (var stack : input.items()) {
            if (TianshuWctIntegration.hasTianshuCrafting(stack)) {
                if (((CraftingRecipe) this).assemble(input, level.registryAccess()).isEmpty()) cir.setReturnValue(false);
                return;
            }
        }
    }
}
