package com.moakiee.ae2lt.mixin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import appeng.api.stacks.AEKey;
import appeng.crafting.execution.ExecutingCraftingJob;

@Mixin(targets = "appeng.crafting.execution.CraftingCpuLogic", remap = false)
public interface CraftingCpuLogicAccessor {
    @Accessor("job")
    @Nullable
    ExecutingCraftingJob getJob();

    @Invoker("finishJob")
    void invokeFinishJob(boolean success);

    @Invoker("postChange")
    void invokePostChange(AEKey what);
}
