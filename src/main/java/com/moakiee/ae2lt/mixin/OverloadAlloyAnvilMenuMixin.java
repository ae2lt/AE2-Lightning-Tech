package com.moakiee.ae2lt.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.moakiee.ae2lt.menu.OverloadAlloyAnvilMenu;
import net.minecraft.world.inventory.AnvilMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AnvilMenu.class)
public abstract class OverloadAlloyAnvilMenuMixin {
    @ModifyExpressionValue(method = "onTake", at = @At(value = "INVOKE", target =
            "Lnet/neoforged/neoforge/common/CommonHooks;onAnvilRepair(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)F"))
    private float ae2lt$keepAlloyAnvil(float breakChance) {
        // The original callback has already run; only our anvil's wear chance changes.
        return (Object) this instanceof OverloadAlloyAnvilMenu ? 0 : breakChance;
    }
}
