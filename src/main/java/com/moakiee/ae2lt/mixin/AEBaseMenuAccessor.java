package com.moakiee.ae2lt.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.world.inventory.Slot;

import appeng.menu.AEBaseMenu;

// Re-exposes the private `isPlayerSideSlot` helper that's already public in 1.21.
@Mixin(AEBaseMenu.class)
public interface AEBaseMenuAccessor {
    @Invoker(value = "isPlayerSideSlot", remap = false)
    boolean ae2lt$isPlayerSideSlot(Slot slot);
}
