package com.moakiee.ae2lt.mixin.thunderbolt.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.client.gui.widgets.CPUSelectionList;
import appeng.menu.me.crafting.CraftingStatusMenu;

import com.moakiee.thunderbolt.core.storage.InfiniteCpuStorageFormat;

@Mixin(value = CPUSelectionList.class, remap = false)
public abstract class CPUSelectionListStorageMixin {
    @Inject(method = "formatStorage", at = @At("HEAD"), cancellable = true)
    private void thunderbolt$formatInfiniteStorage(
            CraftingStatusMenu.CraftingCpuListEntry cpu,
            CallbackInfoReturnable<String> cir) {
        String formatted = InfiniteCpuStorageFormat.format(cpu.storage());
        if (formatted == null) {
            return;
        }

        cir.setReturnValue(formatted);
    }
}
