package com.moakiee.ae2lt.mixin.thunderbolt.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(targets = "cn.dancingsnow.neoecoae.api.me.ECOCraftingCPU", remap = false)
public interface ECOCraftingCpuAccessor {
    @Invoker("markDirty")
    void ae2lt$markDirty();
}
