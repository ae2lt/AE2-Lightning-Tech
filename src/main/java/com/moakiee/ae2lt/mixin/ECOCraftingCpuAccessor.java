package com.moakiee.ae2lt.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Pseudo accessor for NeoECOAEExtension's ECOCraftingCPU.
 * <p>
 * Unlike AdvCraftingCpuAccessor, ECO does not expose an updateOutput hook
 * (NeoECO explicitly comments "Crafting Monitor unsupported"), so only
 * {@code markDirty} is exposed here.
 */
@Pseudo
@Mixin(targets = "cn.dancingsnow.neoecoae.api.me.ECOCraftingCPU", remap = false)
public interface ECOCraftingCpuAccessor {
    @Invoker("markDirty")
    void invokeMarkDirty();
}
