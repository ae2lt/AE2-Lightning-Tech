package com.moakiee.ae2lt.mixin.ae2wtlib;

import com.moakiee.ae2lt.integration.ae2wtlib.TianshuWctIntegration;
import de.mari_023.ae2wtlib.wct.CraftingTerminalHandler;
import de.mari_023.ae2wtlib.wct.magnet_card.MagnetHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = MagnetHandler.class, remap = false)
public abstract class TianshuMagnetHandlerMixin {
    @ModifyVariable(method = "handle", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 1)
    private static ItemStack ae2lt$useLocatedSettings(ItemStack terminal, ServerPlayer player, ItemStack ticked) {
        var selected = CraftingTerminalHandler.getCraftingTerminalHandler(player).getCraftingTerminal();
        return TianshuWctIntegration.hasTianshuCrafting(terminal) || TianshuWctIntegration.hasTianshuCrafting(selected)
                ? selected : terminal;
    }
}
