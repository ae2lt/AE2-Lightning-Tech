package com.moakiee.ae2lt.mixin;

import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.core.network.serverbound.FillCraftingGridFromRecipePacket;
import com.moakiee.ae2lt.menu.PigmeeSynthesisStationMenu;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Use the station's local storage in AE2's shared EMI/JEI fill packet. */
@Mixin(value = FillCraftingGridFromRecipePacket.class, remap = false)
public abstract class PigmeeRecipeTransferStorageMixin {
    // After both network/no-network branches have initialized their locals.
    @ModifyVariable(method = "handleOnServer", at = @At(value = "INVOKE",
            target = "Lappeng/helpers/ICraftingGridMenu;getCraftingMatrix()Lappeng/api/inventories/InternalInventory;"),
            ordinal = 0, require = 1, allow = 1)
    private MEStorage ae2lt$useAdjacentStorage(MEStorage original, ServerPlayer player) {
        if (player.containerMenu instanceof PigmeeSynthesisStationMenu menu
                && menu.getHost().getLinkStatus().connected()) {
            return menu.getHost().getInventory();
        }
        return original;
    }

    @ModifyVariable(method = "handleOnServer", at = @At(value = "INVOKE",
            target = "Lappeng/helpers/ICraftingGridMenu;getCraftingMatrix()Lappeng/api/inventories/InternalInventory;"),
            ordinal = 0, require = 1, allow = 1)
    private KeyCounter ae2lt$countAdjacentIngredients(KeyCounter original, ServerPlayer player) {
        if (player.containerMenu instanceof PigmeeSynthesisStationMenu menu
                && menu.getHost().getLinkStatus().connected()) {
            return menu.getHost().getInventory().getAvailableStacks();
        }
        return original;
    }
}
