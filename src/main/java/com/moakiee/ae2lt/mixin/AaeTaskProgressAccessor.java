package com.moakiee.ae2lt.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob$TaskProgress", remap = false)
public interface AaeTaskProgressAccessor {
    @Accessor("value")
    long getValue();

    @Accessor("value")
    void setValue(long value);
}
