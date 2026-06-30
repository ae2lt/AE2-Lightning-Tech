package com.moakiee.ae2lt.mixin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Future;

import com.google.common.collect.ImmutableSet;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalLongRef;

import org.objectweb.asm.Opcodes;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.CraftingLink;
import appeng.me.service.CraftingService;

import com.moakiee.ae2lt.blockentity.TestTimeWheelCraftingCpuBlockEntity;
import com.moakiee.ae2lt.logic.timewheelcpu.TimeWheelCraftingCPU;
import com.moakiee.ae2lt.logic.timewheelcpu.TimeWheelFastPlanningGate;
import com.qianchang.ae2lt_core.ae2.crafting.FastCraftingControl;

@Mixin(value = CraftingService.class, remap = false)
public abstract class TimeWheelCraftingServiceMixin {
    @Unique
    private static final Comparator<TimeWheelCraftingCPU> AE2LT_TIME_WHEEL_FAST_FIRST = Comparator
            .comparingInt(TimeWheelCraftingCPU::getCoProcessors)
            .reversed()
            .thenComparingLong(TimeWheelCraftingCPU::getAvailableStorage);

    @Unique
    private final Set<TimeWheelCraftingCPU> ae2lt$timeWheelCpus = new HashSet<>();

    @Shadow
    @Final
    private IGrid grid;

    @Shadow
    @Final
    private IEnergyService energyGrid;

    @Shadow
    @Final
    private Set<AEKey> currentlyCrafting;

    @Shadow
    private boolean updateList;

    @Shadow
    public abstract void addLink(CraftingLink link);

    @Inject(
            method = "beginCraftingCalculation",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/concurrent/ExecutorService;submit(Ljava/util/concurrent/Callable;)Ljava/util/concurrent/Future;",
                    shift = At.Shift.BEFORE))
    private void ae2lt$enableFastPlanningForTimeWheelCpu(Level level,
                                                        ICraftingSimulationRequester simRequester,
                                                        AEKey what,
                                                        long amount,
                                                        CalculationStrategy strategy,
                                                        CallbackInfoReturnable<Future<ICraftingPlan>> cir,
                                                        @Local CraftingCalculation job) {
        boolean enabled = TimeWheelFastPlanningGate.shouldEnableFastPlanning(this.ae2lt$timeWheelCpus);
        ((FastCraftingControl) job).ae2lt$setFastPlanningEnabled(enabled);
    }

    @Inject(
            method = "onServerEndTick",
            at = @At(
                    value = "FIELD",
                    target = "Lappeng/me/service/CraftingService;lastProcessedCraftingLogicChangeTick:J",
                    opcode = Opcodes.GETFIELD,
                    ordinal = 0))
    private void ae2lt$tickTimeWheelCpus(CallbackInfo ci, @Local(ordinal = 0) LocalLongRef latestChange) {
        long latest = latestChange.get();
        for (var cpu : this.ae2lt$timeWheelCpus) {
            cpu.getCraftingLogic().tickCraftingLogic(this.energyGrid, (CraftingService) (Object) this);
            latest = Math.max(latest, cpu.getCraftingLogic().getWaitingKeysModifiedOnTick());
        }
        latestChange.set(latest);
    }

    @Inject(
            method = "onServerEndTick",
            at = @At(
                    value = "FIELD",
                    target = "Lappeng/me/service/CraftingService;interests:Lcom/google/common/collect/Multimap;",
                    opcode = Opcodes.GETFIELD,
                    ordinal = 0))
    private void ae2lt$addTimeWheelWaitingKeys(CallbackInfo ci) {
        for (var cpu : this.ae2lt$timeWheelCpus) {
            cpu.getCraftingLogic().getAllWaitingFor(this.currentlyCrafting);
        }
    }

    @Inject(method = "removeNode", at = @At("TAIL"))
    private void ae2lt$onRemoveNode(IGridNode gridNode, CallbackInfo ci) {
        if (gridNode.getOwner() instanceof TestTimeWheelCraftingCpuBlockEntity blockEntity) {
            ae2lt$removeTimeWheelCpu(blockEntity);
            this.updateList = true;
        }
    }

    @Inject(method = "addNode", at = @At("TAIL"))
    private void ae2lt$onAddNode(IGridNode gridNode, CompoundTag savedData, CallbackInfo ci) {
        if (gridNode.getOwner() instanceof TestTimeWheelCraftingCpuBlockEntity blockEntity) {
            ae2lt$addTimeWheelCpu(blockEntity);
            this.updateList = true;
        }
    }

    @Inject(method = "updateCPUClusters", at = @At("TAIL"))
    private void ae2lt$updateTimeWheelCpus(CallbackInfo ci) {
        this.ae2lt$timeWheelCpus.clear();

        for (var blockEntity : this.grid.getMachines(TestTimeWheelCraftingCpuBlockEntity.class)) {
            ae2lt$addTimeWheelCpu(blockEntity);
        }
    }

    @Inject(
            method = "insertIntoCpus",
            at = @At(value = "RETURN", shift = At.Shift.BY, by = -1),
            order = 500)
    private void ae2lt$insertIntoTimeWheelCpus(AEKey what,
                                              long amount,
                                              Actionable type,
                                              CallbackInfoReturnable<Long> cir,
                                              @Local(ordinal = 1) LocalLongRef inserted) {
        for (var cpu : this.ae2lt$timeWheelCpus) {
            if (inserted.get() >= amount) {
                break;
            }
            inserted.set(inserted.get() + cpu.getCraftingLogic().insert(what, amount - inserted.get(), type));
        }
    }

    @Inject(method = "submitJob", at = @At("HEAD"), cancellable = true)
    private void ae2lt$submitToTimeWheelCpu(ICraftingPlan job,
                                            ICraftingRequester requestingMachine,
                                            ICraftingCPU target,
                                            boolean prioritizePower,
                                            IActionSource src,
                                            CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        if (job.simulation()) {
            return;
        }

        if (target instanceof TimeWheelCraftingCPU timeWheelCpu) {
            cir.setReturnValue(timeWheelCpu.submitJob(this.grid, job, src, requestingMachine));
            return;
        }

        if (target != null) {
            return;
        }

        var cpu = ae2lt$findSuitableTimeWheelCpu(job, src);
        if (cpu == null) {
            return;
        }

        var result = cpu.submitJob(this.grid, job, src, requestingMachine);
        if (result.successful()) {
            cir.setReturnValue(result);
        }
    }

    @Inject(
            method = "getCpus",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/google/common/collect/ImmutableSet$Builder;build()Lcom/google/common/collect/ImmutableSet;"))
    private void ae2lt$getTimeWheelCpus(CallbackInfoReturnable<ImmutableSet<ICraftingCPU>> cir,
                                        @Local(ordinal = 0) ImmutableSet.Builder<ICraftingCPU> cpus) {
        for (var cpu : this.ae2lt$timeWheelCpus) {
            if (cpu.isActive()) {
                cpus.add(cpu);
            }
        }
    }

    @Inject(method = "getRequestedAmount", at = @At("RETURN"), cancellable = true)
    private void ae2lt$getTimeWheelRequestedAmount(AEKey what, CallbackInfoReturnable<Long> cir) {
        long requested = cir.getReturnValue();
        for (var cpu : this.ae2lt$timeWheelCpus) {
            requested += cpu.getCraftingLogic().getWaitingFor(what);
        }
        cir.setReturnValue(requested);
    }

    @Inject(method = "hasCpu", at = @At("HEAD"), cancellable = true)
    private void ae2lt$hasTimeWheelCpu(ICraftingCPU cpu, CallbackInfoReturnable<Boolean> cir) {
        if (cpu instanceof TimeWheelCraftingCPU && this.ae2lt$timeWheelCpus.contains(cpu)) {
            cir.setReturnValue(true);
        }
    }

    @Unique
    private void ae2lt$addTimeWheelCpu(TestTimeWheelCraftingCpuBlockEntity blockEntity) {
        var cpu = blockEntity.getCpu();
        cpu.resolvePendingLoad();
        this.ae2lt$timeWheelCpus.add(cpu);

        ICraftingLink maybeLink = cpu.getCraftingLogic().getLastLink();
        if (maybeLink instanceof CraftingLink link) {
            this.addLink(link);
        }
    }

    @Unique
    private void ae2lt$removeTimeWheelCpu(TestTimeWheelCraftingCpuBlockEntity blockEntity) {
        this.ae2lt$timeWheelCpus.remove(blockEntity.getCpu());
    }

    @Unique
    @Nullable
    private TimeWheelCraftingCPU ae2lt$findSuitableTimeWheelCpu(ICraftingPlan job, IActionSource src) {
        var valid = new ArrayList<TimeWheelCraftingCPU>(this.ae2lt$timeWheelCpus.size());
        for (var cpu : this.ae2lt$timeWheelCpus) {
            if (!cpu.isActive()) {
                continue;
            }
            if (cpu.isBusy()) {
                continue;
            }
            if (cpu.getAvailableStorage() < job.bytes()) {
                continue;
            }
            if (!cpu.canBeAutoSelectedFor(src)) {
                continue;
            }
            valid.add(cpu);
        }

        if (valid.isEmpty()) {
            return null;
        }

        valid.sort((a, b) -> {
            var firstPreferred = a.isPreferredFor(src);
            var secondPreferred = b.isPreferredFor(src);
            if (firstPreferred != secondPreferred) {
                return Boolean.compare(secondPreferred, firstPreferred);
            }
            return AE2LT_TIME_WHEEL_FAST_FIRST.compare(a, b);
        });
        return valid.getFirst();
    }
}
