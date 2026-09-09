package com.moakiee.ae2lt.mixin.staff;

import com.moakiee.ae2lt.item.staff.PhaseItemProtection;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "chanceCubes.mcwrapper.InventoryWrapper", remap = false)
abstract class PhaseChanceInventoryCopyMixin {
    // ClearInventoryReward captures once and may restore 200 ticks later. Exclude protected
    // payloads from that capture and preserve newly locked destination slots during restoration.
    @Inject(method = "copyInvAToB", at = @At("HEAD"), cancellable = true)
    private static void ae2lt$copyOnlyUnprotected(Inventory source, Inventory destination, CallbackInfo ci) {
        if (!PhaseItemProtection.containsProtected(source) && !PhaseItemProtection.containsProtected(destination)) return;
        var serialized = new Inventory(source.player);
        serialized.load(source.save(new ListTag()));
        for (int i = 0; i < destination.getContainerSize(); i++) {
            if (PhaseItemProtection.isProtected(destination.getItem(i))) continue;
            destination.setItem(i, PhaseItemProtection.isProtected(source.getItem(i)) ? ItemStack.EMPTY : serialized.getItem(i));
        }
        ci.cancel();
    }
}
