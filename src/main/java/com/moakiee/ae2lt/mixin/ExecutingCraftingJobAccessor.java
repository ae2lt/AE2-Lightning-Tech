package com.moakiee.ae2lt.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import appeng.api.stacks.GenericStack;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.ElapsedTimeTracker;
import appeng.crafting.inv.ListCraftingInventory;

@Mixin(targets = "appeng.crafting.execution.ExecutingCraftingJob", remap = false)
public interface ExecutingCraftingJobAccessor {
    @Accessor("waitingFor")
    ListCraftingInventory getWaitingFor();

    @Accessor("timeTracker")
    ElapsedTimeTracker getTimeTracker();

    @Accessor("finalOutput")
    GenericStack getFinalOutput();

    @Accessor("remainingAmount")
    long getRemainingAmount();

    @Accessor("remainingAmount")
    void setRemainingAmount(long remainingAmount);

    @Accessor("link")
    CraftingLink getLink();
}
