package com.moakiee.ae2lt.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.moakiee.ae2lt.menu.TianshuStonecutterMenu;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(StonecutterMenu.class)
public abstract class TianshuStonecutterMenuMixin {
    @Shadow private ItemStack input;

    @ModifyExpressionValue(method = "slotsChanged", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z"))
    private boolean ae2lt$sameInput(boolean sameItem, Container inventory) {
        return sameItem && (!((Object) this instanceof TianshuStonecutterMenu)
                || ItemStack.isSameItemSameComponents(input, inventory.getItem(0)));
    }
}
