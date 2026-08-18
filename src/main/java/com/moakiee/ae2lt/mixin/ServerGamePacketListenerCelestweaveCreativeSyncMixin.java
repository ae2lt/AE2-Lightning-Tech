package com.moakiee.ae2lt.mixin;

import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.celestweave.BaseCelestweaveArmorItem;
import com.moakiee.ae2lt.celestweave.CelestweaveArmorState;

/**
 * Keeps server-owned Celestweave state authoritative when the creative inventory is opened.
 *
 * <p>Vanilla's {@code CreativeInventoryListener} installs a listener each time the creative
 * inventory opens. The listener immediately broadcasts slots whose client-side snapshot changed
 * while the screen was closed. For equipment with live data components (energy, module state,
 * etc.), that broadcast sends a whole, potentially stale stack back through
 * {@code handleSetCreativeModeSlot}. Vanilla then calls {@link Slot#setByPlayer(ItemStack)}, which
 * can both overwrite the newer server state and make an armor slot call {@code onEquipItem},
 * producing a false equip sound.</p>
 *
 * <p>This wrapper is deliberately placed at the narrow server-side mutation point instead of
 * suppressing the client listener. The server can compare the authoritative stack with the
 * uploaded stack and reject only a state echo of the same UUID-bound armor. Empty stacks, another
 * item, and another armor UUID still reach vanilla, so real creative-mode moves, equips and
 * removals retain their normal behavior.</p>
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerCelestweaveCreativeSyncMixin {
    @WrapOperation(
            method = "handleSetCreativeModeSlot",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/inventory/Slot;setByPlayer(Lnet/minecraft/world/item/ItemStack;)V"))
    private void ae2lt$rejectStaleCelestweaveCreativeEcho(
            Slot slot,
            ItemStack uploaded,
            Operation<Void> original) {
        ItemStack authoritative = slot.getItem();
        if (!ae2lt$isSameCelestweaveArmor(authoritative, uploaded)) {
            original.call(slot, uploaded);
            return;
        }

        /*
         * handleSetCreativeModeSlot calls broadcastChanges immediately after this invocation.
         * Invalidate only this remote cache entry so that broadcast sends the authoritative stack
         * back even when the server had already queued an earlier version for the client.
         */
        var listener = (ServerGamePacketListenerImpl) (Object) this;
        listener.player.inventoryMenu.setRemoteSlot(slot.index, ItemStack.EMPTY);
    }

    @Unique
    private static boolean ae2lt$isSameCelestweaveArmor(ItemStack authoritative, ItemStack uploaded) {
        if (authoritative.isEmpty()
                || uploaded.isEmpty()
                || authoritative.getItem() != uploaded.getItem()
                || !(authoritative.getItem() instanceof BaseCelestweaveArmorItem)) {
            return false;
        }

        UUID authoritativeId = CelestweaveArmorState.getArmorId(authoritative);
        UUID uploadedId = CelestweaveArmorState.getArmorId(uploaded);
        return authoritativeId != null && authoritativeId.equals(uploadedId);
    }
}
