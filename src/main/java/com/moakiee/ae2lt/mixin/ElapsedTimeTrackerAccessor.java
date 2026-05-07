package com.moakiee.ae2lt.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import appeng.api.stacks.AEKeyType;

@Mixin(targets = "appeng.crafting.execution.ElapsedTimeTracker", remap = false)
public interface ElapsedTimeTrackerAccessor {
    @Invoker("decrementItems")
    void invokeDecrementItems(long amount, AEKeyType keyType);
}
