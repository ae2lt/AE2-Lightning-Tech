package com.moakiee.ae2lt.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

import appeng.api.stacks.GenericStack;

@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPU", remap = false)
public interface AdvCraftingCpuAccessor {
    @Invoker("markDirty")
    void invokeMarkDirty();

    @Invoker("updateOutput")
    void invokeUpdateOutput(GenericStack stack);
}
