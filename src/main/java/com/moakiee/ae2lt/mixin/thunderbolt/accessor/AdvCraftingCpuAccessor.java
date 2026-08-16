package com.moakiee.ae2lt.mixin.thunderbolt.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

import appeng.api.stacks.GenericStack;

@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPU", remap = false)
public interface AdvCraftingCpuAccessor {
    @Invoker("markDirty")
    void ae2lt$markDirty();

    @Invoker("updateOutput")
    void ae2lt$updateOutput(GenericStack stack);
}
