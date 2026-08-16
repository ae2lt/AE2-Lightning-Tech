package com.moakiee.ae2lt.mixin.thunderbolt.accessor;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.ElapsedTimeTracker;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ListCraftingInventory;

@Mixin(value = ExecutingCraftingJob.class, remap = false)
public interface ExecutingCraftingJobAccessor {
    @Accessor("waitingFor")
    ListCraftingInventory ae2lt$getWaitingFor();

    @Accessor("timeTracker")
    ElapsedTimeTracker ae2lt$getTimeTracker();

    @Accessor("finalOutput")
    GenericStack ae2lt$getFinalOutput();

    @Accessor("remainingAmount")
    long ae2lt$getRemainingAmount();

    @Accessor("remainingAmount")
    void ae2lt$setRemainingAmount(long remainingAmount);

    @Accessor("link")
    CraftingLink ae2lt$getLink();

    @Accessor("tasks")
    Map<IPatternDetails, ?> ae2lt$getTasks();
}
