package com.moakiee.ae2lt.mixin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;

import com.moakiee.ae2lt.overload.cpu.InsertContext;
import com.moakiee.ae2lt.overload.cpu.OverloadClaimResult;
import com.moakiee.ae2lt.overload.cpu.OverloadCpuStateManager;
import com.moakiee.ae2lt.overload.cpu.OverloadPatternReference;
import com.moakiee.ae2lt.overload.pattern.OverloadedProviderOnlyPatternDetails;

@Mixin(targets = "appeng.crafting.execution.CraftingCpuLogic", remap = false)
public abstract class CraftingCpuLogicMixin {
    @Shadow(remap = false)
    CraftingCPUCluster cluster;

    @Unique
    @Nullable
    private InsertContext ae2lt$insertContext;

    @Inject(method = "insert", at = @At("HEAD"))
    private void ae2lt$beginInsertContext(AEKey what, long amount, Actionable type,
                                          CallbackInfoReturnable<Long> cir) {
        this.ae2lt$insertContext = new InsertContext(what, amount, type);
    }

    @WrapOperation(
            method = "insert",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/crafting/inv/ListCraftingInventory;extract(Lappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;)J",
                    ordinal = 0
            ),
            remap = false
    )
    private long ae2lt$captureStrictWaitingMatch(ListCraftingInventory waitingFor, AEKey what, long amount,
                                                 Actionable mode, Operation<Long> original) {
        long strictMatched = original.call(waitingFor, what, amount, mode);
        if (mode == Actionable.SIMULATE && this.ae2lt$insertContext != null) {
            this.ae2lt$insertContext.setStrictMatched(strictMatched);
        }
        return strictMatched;
    }

    @Inject(method = "insert", at = @At("RETURN"), cancellable = true)
    private void ae2lt$claimOverloadRemainder(AEKey what, long amount, Actionable type,
                                              CallbackInfoReturnable<Long> cir) {
        var ctx = this.ae2lt$insertContext;
        this.ae2lt$insertContext = null;
        if (ctx == null || ctx.getRequestedAmount() <= 0) {
            return;
        }

        long remainder = Math.max(0L, ctx.getRequestedAmount() - ctx.getStrictMatched());
        if (remainder <= 0) {
            return;
        }

        var logic = (appeng.crafting.execution.CraftingCpuLogic) (Object) this;
        if (!OverloadCpuStateManager.INSTANCE.hasAnyPending(logic)) {
            return;
        }

        var claims = OverloadCpuStateManager.INSTANCE.claim(logic, what, remainder, type);
        if (!claims.claimedAnything()) {
            return;
        }

        long supplementalReturn = 0;
        if (type == Actionable.MODULATE) {
            var job = ((CraftingCpuLogicAccessor) logic).getJob();
            if (job != null) {
                ae2lt$deductClaimedWaitingFor(job, claims);
            }
            supplementalReturn += ae2lt$applyInventoryClaims(what, claims);
            supplementalReturn += ae2lt$applyRequesterClaims(what, claims);
            cluster.markDirty();
        } else {
            supplementalReturn += claims.claimedAmount();
        }

        cir.setReturnValue(cir.getReturnValue() + supplementalReturn);
    }

    @WrapOperation(
            method = "executeCrafting",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/api/networking/crafting/ICraftingProvider;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z"
            ),
            remap = false
    )
    private boolean ae2lt$registerOverloadExpectedOutputs(ICraftingProvider provider, IPatternDetails details,
                                                          KeyCounter[] inputHolder, Operation<Boolean> original) {
        var logic = (appeng.crafting.execution.CraftingCpuLogic) (Object) this;
        OverloadPatternReference patternReference = null;
        if (details instanceof OverloadedProviderOnlyPatternDetails overloadDetails) {
            patternReference = new OverloadPatternReference(
                    overloadDetails.overloadPatternIdentity(),
                    overloadDetails.overloadPatternDetailsView().sourcePattern());
            if (OverloadCpuStateManager.INSTANCE.hasAmbiguousOutputRegistration(
                    logic,
                    patternReference,
                    overloadDetails.overloadPatternDetailsView())) {
                return false;
            }
        }

        boolean pushed = original.call(provider, details, inputHolder);
        if (pushed && details instanceof OverloadedProviderOnlyPatternDetails overloadDetails) {
            var job = ((CraftingCpuLogicAccessor) logic).getJob();
            var finalOutput = job != null
                    ? ((ExecutingCraftingJobAccessor) job).getFinalOutput()
                    : null;
            var finalOutputKey = finalOutput != null ? finalOutput.what() : null;
            OverloadCpuStateManager.INSTANCE.registerExpectedOutputs(
                    logic,
                    patternReference != null
                            ? patternReference
                            : new OverloadPatternReference(
                                    overloadDetails.overloadPatternIdentity(),
                                    overloadDetails.overloadPatternDetailsView().sourcePattern()),
                    overloadDetails.overloadPatternDetailsView(),
                    details.getOutputs(),
                    finalOutputKey,
                    1L);
        }
        return pushed;
    }

    @Inject(method = "writeToNBT", at = @At("RETURN"))
    private void ae2lt$writeOverloadState(CompoundTag data, HolderLookup.Provider registries, CallbackInfo ci) {
        var logic = (appeng.crafting.execution.CraftingCpuLogic) (Object) this;
        var overloadStateTag = OverloadCpuStateManager.INSTANCE.writeToTag(logic, registries);
        if (overloadStateTag != null) {
            data.put("ae2ltOverloadState", overloadStateTag);
        } else {
            data.remove("ae2ltOverloadState");
        }
    }

    @Inject(method = "readFromNBT", at = @At("RETURN"))
    private void ae2lt$readOverloadState(CompoundTag data, HolderLookup.Provider registries, CallbackInfo ci) {
        var logic = (appeng.crafting.execution.CraftingCpuLogic) (Object) this;
        OverloadCpuStateManager.INSTANCE.clear(logic);
        var job = ((CraftingCpuLogicAccessor) logic).getJob();
        if (job != null && data.contains("ae2ltOverloadState", CompoundTag.TAG_COMPOUND)) {
            OverloadCpuStateManager.INSTANCE.readFromTag(logic, data.getCompound("ae2ltOverloadState"), registries);
        }
    }

    @Inject(method = "finishJob", at = @At("HEAD"))
    private void ae2lt$clearOverloadState(boolean success, CallbackInfo ci) {
        OverloadCpuStateManager.INSTANCE.clear((appeng.crafting.execution.CraftingCpuLogic) (Object) this);
    }

    @Unique
    private long ae2lt$applyInventoryClaims(AEKey incoming, OverloadClaimResult claims) {
        long claimed = claims.claimedForInventory();
        if (claimed <= 0) {
            return 0;
        }

        var logic = (appeng.crafting.execution.CraftingCpuLogic) (Object) this;
        var job = ((CraftingCpuLogicAccessor) logic).getJob();
        if (job == null) {
            return 0;
        }

        var jobAccessor = (ExecutingCraftingJobAccessor) job;
        ((ElapsedTimeTrackerAccessor) jobAccessor.getTimeTracker()).invokeDecrementItems(
                claimed,
                incoming.getType());
        logic.getInventory().insert(incoming, claimed, Actionable.MODULATE);
        return claimed;
    }

    @Unique
    private long ae2lt$applyRequesterClaims(AEKey incoming, OverloadClaimResult claims) {
        long claimed = claims.claimedForRequester();
        if (claimed <= 0) {
            return 0;
        }

        var logic = (appeng.crafting.execution.CraftingCpuLogic) (Object) this;
        var logicAccessor = (CraftingCpuLogicAccessor) logic;
        ExecutingCraftingJob job = logicAccessor.getJob();
        if (job == null) {
            return 0;
        }

        var jobAccessor = (ExecutingCraftingJobAccessor) job;
        ((ElapsedTimeTrackerAccessor) jobAccessor.getTimeTracker()).invokeDecrementItems(
                claimed,
                incoming.getType());
        var link = jobAccessor.getLink();
        long inserted = link != null ? link.insert(incoming, claimed, Actionable.MODULATE) : 0;
        logicAccessor.invokePostChange(incoming);

        long remaining = Math.max(0L, jobAccessor.getRemainingAmount() - claimed);
        jobAccessor.setRemainingAmount(remaining);

        if (remaining <= 0) {
            logicAccessor.invokeFinishJob(true);
            cluster.updateOutput(null);
        } else {
            GenericStack finalOutput = jobAccessor.getFinalOutput();
            if (finalOutput != null) {
                cluster.updateOutput(new GenericStack(finalOutput.what(), remaining));
            }
        }

        return inserted;
    }

    @Unique
    private void ae2lt$deductClaimedWaitingFor(ExecutingCraftingJob job, OverloadClaimResult claims) {
        var waitingFor = ((ExecutingCraftingJobAccessor) job).getWaitingFor();
        for (var claim : claims.claims()) {
            waitingFor.extract(claim.exactExpectedKey(), claim.claimedAmount(), Actionable.MODULATE);
        }
    }

}
