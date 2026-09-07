package com.moakiee.ae2lt.mixin;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.StorageHelper;
import appeng.core.sync.packets.FillCraftingGridFromRecipePacket;
import appeng.helpers.IMenuCraftingPacket;
import appeng.util.prioritylist.IPartitionList;
import com.moakiee.ae2lt.blockentity.PigmeeSynthesisStationBlockEntity;
import com.moakiee.ae2lt.menu.PigmeeSynthesisStationMenu;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** AE2 15's fill packet requires a grid; route only station menus through local storage. */
@Mixin(value = FillCraftingGridFromRecipePacket.class, remap = false)
public abstract class PigmeeRecipeTransferStorageMixin {
    @Shadow
    protected abstract NonNullList<Ingredient> getDesiredIngredients(Player player);

    @Shadow
    protected abstract List<AEItemKey> findBestMatchingItemStack(
            Ingredient ingredient, IPartitionList filter, KeyCounter storage);

    @Shadow
    protected abstract ItemStack takeIngredientFromPlayer(
            IMenuCraftingPacket menu, ServerPlayer player, Ingredient ingredient);

    @Inject(method = "serverPacketData", at = @At("HEAD"), cancellable = true, require = 1)
    private void ae2lt$fillFromAdjacentStorage(ServerPlayer player, CallbackInfo ci) {
        if (!(player.containerMenu instanceof PigmeeSynthesisStationMenu menu)) return;
        ci.cancel();
        if (!menu.stillValid(player)) return;
        var host = (PigmeeSynthesisStationBlockEntity) menu.getHost();
        if (!host.hasAdjacentStorage()) return;
        var storage = host.getInventory();
        var matrix = menu.getCraftingMatrix();
        var ingredients = getDesiredIngredients(player);
        for (int slot = 0; slot < matrix.size(); slot++) {
            var current = matrix.getStackInSlot(slot);
            var ingredient = ingredients.get(slot);
            if (!current.isEmpty()) {
                if (ingredient.test(current)) continue;
                long inserted = StorageHelper.poweredInsert(host, storage, AEItemKey.of(current),
                        current.getCount(), menu.getActionSource());
                current = current.copy();
                current.shrink((int) inserted);
                player.getInventory().add(current);
                matrix.setItemDirect(slot, current.isEmpty() ? ItemStack.EMPTY : current);
            }
            if (ingredient.isEmpty()) continue;
            if (current.isEmpty()) {
                for (var key : findBestMatchingItemStack(ingredient, null, storage.getAvailableStacks())) {
                    long extracted = StorageHelper.poweredExtraction(
                            host, storage, key, 1, menu.getActionSource());
                    if (extracted > 0) {
                        current = key.toStack((int) extracted);
                        break;
                    }
                }
            }
            if (current.isEmpty()) current = takeIngredientFromPlayer(menu, player, ingredient);
            matrix.setItemDirect(slot, current);
        }
        menu.slotsChanged(matrix.toContainer());
    }
}
