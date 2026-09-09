package com.moakiee.ae2lt.mixin.staff;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moakiee.ae2lt.item.staff.PhaseItemProtection;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class PhaseServerMenuMixin {
    // The packet dispatcher also covers menus which override clicked without calling super.
    @WrapOperation(method = "handleContainerClick", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/inventory/AbstractContainerMenu;clicked(IILnet/minecraft/world/inventory/ClickType;Lnet/minecraft/world/entity/player/Player;)V"))
    private void ae2lt$admitPacketTransfer(AbstractContainerMenu menu, int slot, int button, ClickType type,
            Player player, Operation<Void> original) {
        if (!PhaseItemProtection.blocksClick(menu, slot, button, type, player)) original.call(menu, slot, button, type, player);
        else menu.sendAllDataToRemote();
    }

    @WrapOperation(method = "handleSetCreativeModeSlot", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/inventory/Slot;setByPlayer(Lnet/minecraft/world/item/ItemStack;)V"))
    private void ae2lt$rejectCreativeTransfer(Slot slot, ItemStack incoming, Operation<Void> original) {
        if (!PhaseItemProtection.isProtected(slot.getItem()) && !PhaseItemProtection.isProtected(incoming)) {
            original.call(slot, incoming);
        } else {
            ((ServerGamePacketListenerImpl) (Object) this).player.inventoryMenu.setRemoteSlot(slot.index, ItemStack.EMPTY);
        }
    }
}
