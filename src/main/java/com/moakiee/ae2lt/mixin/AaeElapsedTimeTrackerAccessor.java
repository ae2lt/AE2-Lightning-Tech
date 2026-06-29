package com.moakiee.ae2lt.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

import appeng.api.stacks.AEKeyType;

@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.logic.ElapsedTimeTracker", remap = false)
public interface AaeElapsedTimeTrackerAccessor {
    @Invoker("addMaxItems")
    void invokeAddMaxItems(long amount, AEKeyType keyType);
}
