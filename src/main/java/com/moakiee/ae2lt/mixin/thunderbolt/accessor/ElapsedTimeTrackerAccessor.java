package com.moakiee.ae2lt.mixin.thunderbolt.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import appeng.api.stacks.AEKeyType;
import appeng.crafting.execution.ElapsedTimeTracker;

@Mixin(value = ElapsedTimeTracker.class, remap = false)
public interface ElapsedTimeTrackerAccessor {
    @Invoker("decrementItems")
    void ae2lt$decrementItems(long amount, AEKeyType keyType);

    @Invoker("addMaxItems")
    void ae2lt$addMaxItems(long amount, AEKeyType keyType);
}
