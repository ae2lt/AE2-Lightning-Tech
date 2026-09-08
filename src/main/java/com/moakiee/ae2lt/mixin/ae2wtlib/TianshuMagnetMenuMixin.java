package com.moakiee.ae2lt.mixin.ae2wtlib;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.moakiee.ae2lt.integration.ae2wtlib.TianshuMagnetMenuHost;
import de.mari_023.ae2wtlib.wct.WCTMenuHost;
import de.mari_023.ae2wtlib.wct.magnet_card.MagnetHost;
import de.mari_023.ae2wtlib.wct.magnet_card.MagnetMenu;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MagnetMenu.class)
public abstract class TianshuMagnetMenuMixin {
    @ModifyExpressionValue(method = "<init>", at = @At(value = "INVOKE",
            target = "Lde/mari_023/ae2wtlib/wct/CraftingTerminalHandler;getMagnetHost()Lde/mari_023/ae2wtlib/wct/magnet_card/MagnetHost;"), require = 1)
    private MagnetHost ae2lt$bindOpenTerminal(MagnetHost original, int id, Inventory inventory, WCTMenuHost host) {
        return host instanceof TianshuMagnetMenuHost tianshu ? tianshu.getBoundMagnetHost() : original;
    }
}
