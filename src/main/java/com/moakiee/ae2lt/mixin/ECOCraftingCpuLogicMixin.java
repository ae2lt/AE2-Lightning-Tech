package com.moakiee.ae2lt.mixin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ListCraftingInventory;

import com.moakiee.ae2lt.util.MixinReflectionSupport;
import com.moakiee.ae2lt.overload.cpu.InsertContext;
import com.moakiee.ae2lt.overload.cpu.OverloadClaimResult;
import com.moakiee.ae2lt.overload.cpu.OverloadCpuStateManager;
import com.moakiee.ae2lt.overload.cpu.OverloadPatternReference;
import com.moakiee.ae2lt.overload.pattern.OverloadedProviderOnlyPatternDetails;

/**
 * Pseudo mixin that grafts the AE2LT overload pattern ID-only claim logic onto
 * NeoECOAEExtension's {@code ECOCraftingCPULogic}. Mirrors the seven injection
 * points used by {@link CraftingCpuLogicMixin} and {@link AdvCraftingCpuLogicMixin}.
 * <p>
 * Critical simplification compared to the AdvancedAE variant: ECO's logic uses
 * AE2's own {@code appeng.crafting.execution.ExecutingCraftingJob} for its
 * {@code job} field (via {@code import appeng.crafting.execution.*;}), so the
 * job's internals can be reached through the existing
 * {@link ExecutingCraftingJobAccessor} / {@link ElapsedTimeTrackerAccessor}
 * accessors instead of more reflection. ECO also lacks an {@code updateOutput}
 * hook, so the corresponding monitor-update calls are intentionally omitted.
 */
@Pseudo
@Mixin(targets = "cn.dancingsnow.neoecoae.api.me.ECOCraftingCPULogic", remap = false)
public abstract class ECOCraftingCpuLogicMixin {

    // --- Reflection lookups: null-safe; failure disables the feature gracefully ---

    @Unique
    private static final @Nullable Class<?> AE2LT_ECO_LOGIC_CLASS =
            MixinReflectionSupport.findClassSafe("cn.dancingsnow.neoecoae.api.me.ECOCraftingCPULogic");

    @Unique
    private static final @Nullable Field AE2LT_ECO_JOB_FIELD =
            MixinReflectionSupport.findDeclaredFieldSafe(AE2LT_ECO_LOGIC_CLASS, "job");

    @Unique
    private static final @Nullable Field AE2LT_ECO_INVENTORY_FIELD =
            MixinReflectionSupport.findDeclaredFieldSafe(AE2LT_ECO_LOGIC_CLASS, "inventory");

    @Unique
    private static final @Nullable Field AE2LT_ECO_CPU_FIELD =
            MixinReflectionSupport.findDeclaredFieldSafe(AE2LT_ECO_LOGIC_CLASS, "cpu");

    @Unique
    private static final @Nullable Method AE2LT_ECO_FINISH_JOB_METHOD =
            MixinReflectionSupport.findDeclaredMethodSafe(AE2LT_ECO_LOGIC_CLASS, "finishJob", boolean.class);

    @Unique
    private static final @Nullable Method AE2LT_ECO_POST_CHANGE_METHOD =
            MixinReflectionSupport.findDeclaredMethodSafe(AE2LT_ECO_LOGIC_CLASS, "postChange", AEKey.class);

    /**
     * Whether all required reflection targets are available.
     * If false, every injection handler short-circuits to a no-op so the mixin
     * is effectively inert when NeoECOAEExtension is absent or has changed shape.
     */
    @Unique
    private static final boolean AE2LT_ECO_AVAILABLE = AE2LT_ECO_LOGIC_CLASS != null
            && AE2LT_ECO_JOB_FIELD != null
            && AE2LT_ECO_INVENTORY_FIELD != null
            && AE2LT_ECO_CPU_FIELD != null
            && AE2LT_ECO_FINISH_JOB_METHOD != null
            && AE2LT_ECO_POST_CHANGE_METHOD != null;

    @Unique
    @Nullable
    private InsertContext ae2lt$insertContext;

    // ========================= Injection Handlers =========================

    @Inject(method = "insert", at = @At("HEAD"))
    private void ae2lt$beginInsertContext(AEKey what, long amount, Actionable type,
                                          CallbackInfoReturnable<Long> cir) {
        if (!AE2LT_ECO_AVAILABLE) return;
        this.ae2lt$insertContext = new InsertContext(what, amount, type);
    }

    @WrapOperation(
            method = "insert",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/crafting/inv/ListCraftingInventory;extract(Lappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;)J",
                    ordinal = 0),
            remap = false)
    private long ae2lt$captureStrictWaitingMatch(ListCraftingInventory waitingFor, AEKey what, long amount,
                                                 Actionable mode, Operation<Long> original) {
        long strictMatched = original.call(waitingFor, what, amount, mode);
        if (AE2LT_ECO_AVAILABLE && mode == Actionable.SIMULATE && this.ae2lt$insertContext != null) {
            this.ae2lt$insertContext.setStrictMatched(strictMatched);
        }
        return strictMatched;
    }

    @Inject(method = "insert", at = @At("RETURN"), cancellable = true)
    private void ae2lt$claimOverloadRemainder(AEKey what, long amount, Actionable type,
                                              CallbackInfoReturnable<Long> cir) {
        if (!AE2LT_ECO_AVAILABLE) return;

        var ctx = this.ae2lt$insertContext;
        this.ae2lt$insertContext = null;
        if (ctx == null || ctx.getRequestedAmount() <= 0) {
            return;
        }

        long remainder = Math.max(0L, ctx.getRequestedAmount() - ctx.getStrictMatched());
        if (remainder <= 0) {
            return;
        }

        if (!OverloadCpuStateManager.INSTANCE.hasAnyPending(this)) {
            return;
        }

        var claims = OverloadCpuStateManager.INSTANCE.claim(this, what, remainder, type);
        if (!claims.claimedAnything()) {
            return;
        }

        if (type == Actionable.MODULATE) {
            ae2lt$deductClaimedWaitingFor(claims);
            long supplementalReturn = ae2lt$applyInventoryClaims(what, claims) + ae2lt$applyRequesterClaims(what, claims);
            var cpu = ae2lt$getCpu();
            if (cpu != null) {
                ((ECOCraftingCpuAccessor) cpu).invokeMarkDirty();
            }
            cir.setReturnValue(cir.getReturnValue() + supplementalReturn);
        } else {
            cir.setReturnValue(cir.getReturnValue() + claims.claimedAmount());
        }
    }

    @WrapOperation(
            method = "executeCrafting",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/api/networking/crafting/ICraftingProvider;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z"),
            remap = false)
    private boolean ae2lt$registerOverloadExpectedOutputs(ICraftingProvider provider, IPatternDetails details,
                                                          KeyCounter[] inputHolder, Operation<Boolean> original) {
        if (!AE2LT_ECO_AVAILABLE) {
            return original.call(provider, details, inputHolder);
        }

        OverloadPatternReference patternReference = null;
        if (details instanceof OverloadedProviderOnlyPatternDetails overloadDetails) {
            patternReference = new OverloadPatternReference(
                    overloadDetails.overloadPatternIdentity(),
                    overloadDetails.overloadPatternDetailsView().sourcePattern());
            if (OverloadCpuStateManager.INSTANCE.hasAmbiguousOutputRegistration(
                    this,
                    patternReference,
                    overloadDetails.overloadPatternDetailsView())) {
                return false;
            }
        }

        boolean pushed = original.call(provider, details, inputHolder);
        var job = ae2lt$getJob();
        if (pushed && details instanceof OverloadedProviderOnlyPatternDetails overloadDetails && job != null) {
            var jobAccessor = (ExecutingCraftingJobAccessor) job;
            GenericStack finalOutput = jobAccessor.getFinalOutput();
            AEKey finalOutputKey = finalOutput != null ? finalOutput.what() : null;
            CraftingLink link = jobAccessor.getLink();
            if (link != null) {
                UUID craftingId = link.getCraftingID();
                OverloadCpuStateManager.INSTANCE.registerExpectedOutputs(
                        this,
                        craftingId,
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
        }
        return pushed;
    }

    @Inject(method = "writeToNBT", at = @At("RETURN"))
    private void ae2lt$writeOverloadState(CompoundTag data, HolderLookup.Provider registries, CallbackInfo ci) {
        if (!AE2LT_ECO_AVAILABLE) return;
        var overloadStateTag = OverloadCpuStateManager.INSTANCE.writeToTag(this, registries);
        if (overloadStateTag != null) {
            data.put("ae2ltOverloadState", overloadStateTag);
        } else {
            data.remove("ae2ltOverloadState");
        }
    }

    @Inject(method = "readFromNBT", at = @At("RETURN"))
    private void ae2lt$readOverloadState(CompoundTag data, HolderLookup.Provider registries, CallbackInfo ci) {
        if (!AE2LT_ECO_AVAILABLE) return;
        OverloadCpuStateManager.INSTANCE.clear(this);
        var job = ae2lt$getJob();
        if (job != null && data.contains("ae2ltOverloadState", CompoundTag.TAG_COMPOUND)) {
            CraftingLink link = ((ExecutingCraftingJobAccessor) job).getLink();
            if (link != null) {
                OverloadCpuStateManager.INSTANCE.readFromTag(
                        this,
                        link.getCraftingID(),
                        data.getCompound("ae2ltOverloadState"),
                        registries);
            }
        }
    }

    @Inject(method = "finishJob", at = @At("HEAD"))
    private void ae2lt$clearOverloadState(boolean success, CallbackInfo ci) {
        if (!AE2LT_ECO_AVAILABLE) return;
        OverloadCpuStateManager.INSTANCE.clear(this);
    }

    // ========================= Claim Application =========================

    @Unique
    private long ae2lt$applyInventoryClaims(AEKey incoming, OverloadClaimResult claims) {
        long claimed = claims.claimedForInventory();
        var job = ae2lt$getJob();
        if (claimed <= 0 || job == null) {
            return 0;
        }

        ae2lt$decrementJobItems(job, claimed, incoming.getType());
        var inventory = ae2lt$getInventory();
        if (inventory != null) {
            inventory.insert(incoming, claimed, Actionable.MODULATE);
        }
        return claimed;
    }

    @Unique
    private long ae2lt$applyRequesterClaims(AEKey incoming, OverloadClaimResult claims) {
        long claimed = claims.claimedForRequester();
        var job = ae2lt$getJob();
        if (claimed <= 0 || job == null) {
            return 0;
        }

        ae2lt$decrementJobItems(job, claimed, incoming.getType());
        var jobAccessor = (ExecutingCraftingJobAccessor) job;
        CraftingLink link = jobAccessor.getLink();
        long inserted = link != null ? link.insert(incoming, claimed, Actionable.MODULATE) : 0;
        ae2lt$invokePostChange(incoming);

        long remaining = Math.max(0L, jobAccessor.getRemainingAmount() - claimed);
        jobAccessor.setRemainingAmount(remaining);

        if (remaining <= 0) {
            ae2lt$invokeFinishJob(true);
        }
        // ECO has no updateOutput hook (Crafting Monitor unsupported), so
        // partial-progress final-output stack is intentionally not pushed.

        return inserted;
    }

    @Unique
    private void ae2lt$deductClaimedWaitingFor(OverloadClaimResult claims) {
        var job = ae2lt$getJob();
        if (job == null) {
            return;
        }

        ListCraftingInventory waitingFor = ((ExecutingCraftingJobAccessor) job).getWaitingFor();
        if (waitingFor == null) return;

        for (var claim : claims.claims()) {
            waitingFor.extract(claim.exactExpectedKey(), claim.claimedAmount(), Actionable.MODULATE);
        }
    }

    @Unique
    private void ae2lt$decrementJobItems(ExecutingCraftingJob job, long amount, AEKeyType keyType) {
        var jobAccessor = (ExecutingCraftingJobAccessor) job;
        var timeTracker = jobAccessor.getTimeTracker();
        if (timeTracker != null) {
            ((ElapsedTimeTrackerAccessor) timeTracker).invokeDecrementItems(amount, keyType);
        }
    }

    // ========================= Reflection Accessors (null-safe) =========================

    @Unique
    @Nullable
    private ExecutingCraftingJob ae2lt$getJob() {
        Object val = MixinReflectionSupport.getFieldValueSafe(AE2LT_ECO_JOB_FIELD, this);
        return val instanceof ExecutingCraftingJob ecj ? ecj : null;
    }

    @Unique
    @Nullable
    private ListCraftingInventory ae2lt$getInventory() {
        Object val = MixinReflectionSupport.getFieldValueSafe(AE2LT_ECO_INVENTORY_FIELD, this);
        return val instanceof ListCraftingInventory inv ? inv : null;
    }

    @Unique
    @Nullable
    private Object ae2lt$getCpu() {
        return MixinReflectionSupport.getFieldValueSafe(AE2LT_ECO_CPU_FIELD, this);
    }

    @Unique
    private void ae2lt$invokeFinishJob(boolean success) {
        MixinReflectionSupport.invokeMethodSafe(AE2LT_ECO_FINISH_JOB_METHOD, this, "finish ECO job", success);
    }

    @Unique
    private void ae2lt$invokePostChange(AEKey what) {
        MixinReflectionSupport.invokeMethodSafe(AE2LT_ECO_POST_CHANGE_METHOD, this, "ECO post change", what);
    }
}
