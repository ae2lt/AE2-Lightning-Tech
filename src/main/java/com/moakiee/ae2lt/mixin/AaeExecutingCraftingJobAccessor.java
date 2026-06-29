package com.moakiee.ae2lt.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.pedroksl.advanced_ae.common.logic.ElapsedTimeTracker;

import appeng.api.crafting.IPatternDetails;
import appeng.crafting.inv.ListCraftingInventory;

@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob", remap = false)
public interface AaeExecutingCraftingJobAccessor {
    @Accessor("tasks")
    Map<IPatternDetails, ?> getTasks();

    @Accessor("waitingFor")
    ListCraftingInventory getWaitingFor();

    @Accessor("timeTracker")
    ElapsedTimeTracker getTimeTracker();
}
