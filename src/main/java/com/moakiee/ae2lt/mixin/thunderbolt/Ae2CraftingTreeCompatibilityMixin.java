package com.moakiee.ae2lt.mixin.thunderbolt;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.menu.me.crafting.CraftingPlanSummary;

import com.moakiee.ae2lt.crafting.runtime.LoopCraftingPlan;

/**
 * Keeps AE2: Crafting Tree's confirmation-summary hook compatible with closed-loop plans.
 *
 * <p>AE2CT declares its hook parameter as {@link ICraftingPlan}, but then unconditionally casts it
 * to AE2's final {@code CraftingPlan} record. A loop plan cannot inherit from that record. The
 * summary only reads the ordinary plan data, so expose the loop plan's delegate inside this one
 * method call. {@code CraftConfirmMenu.result} still retains the original {@link LoopCraftingPlan}
 * and submits that restricted plan to the time-wheel CPU.
 */
@Mixin(value = CraftingPlanSummary.class, remap = false)
public abstract class Ae2CraftingTreeCompatibilityMixin {
    @ModifyVariable(method = "fromJob", at = @At("HEAD"), argsOnly = true)
    private static ICraftingPlan thunderbolt$unwrapLoopPlanForAe2CraftingTree(ICraftingPlan job) {
        if (job instanceof LoopCraftingPlan loopPlan) {
            return loopPlan.delegate();
        }
        return job;
    }
}
