package com.moakiee.ae2lt.mixin.thunderbolt;

import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.Future;

import com.google.common.collect.ImmutableSet;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalLongRef;

import org.apache.commons.lang3.mutable.MutableObject;
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
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.crafting.UnsuitableCpus;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;

import com.moakiee.ae2lt.crafting.runtime.CraftingCpuSelectionOrder;
import com.moakiee.ae2lt.crafting.runtime.DynamicCraftingCpuClusterIndex;
import com.moakiee.ae2lt.crafting.runtime.ExtendedCraftingCpuCluster;
import com.moakiee.ae2lt.crafting.runtime.ExtendedCraftingCpuClusterProvider;
import com.moakiee.thunderbolt.mixin.ae2.crafting.support.FastCraftingControl;
import com.moakiee.ae2lt.crafting.runtime.LoopCraftingPlan;

@Mixin(value = CraftingService.class, remap = false)
public abstract class ExtendedCraftingCpuServiceMixin {
    @Unique
    @Nullable
    private DynamicCraftingCpuClusterIndex<IGridNode, ExtendedCraftingCpuCluster>
            thunderbolt$extendedCpuClusterIndex;

    @Unique
    private long thunderbolt$lastExtendedCraftingLogicChangeTick;

    @Unique
    private boolean thunderbolt$lastExtendedCraftingLogicChangeTickInitialized;

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
    private long lastProcessedCraftingLogicChangeTick;

    @Shadow
    public abstract void addLink(CraftingLink link);

    @Inject(
            method = "beginCraftingCalculation",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/concurrent/ExecutorService;submit(Ljava/util/concurrent/Callable;)Ljava/util/concurrent/Future;",
                    shift = At.Shift.BEFORE))
    private void thunderbolt$enableFastPlanningForTimeWheelCpu(Level level,
                                                               ICraftingSimulationRequester simRequester,
                                                               AEKey what,
                                                               long amount,
                                                               CalculationStrategy strategy,
                                                               CallbackInfoReturnable<Future<ICraftingPlan>> cir,
                                                               @Local CraftingCalculation job) {
        thunderbolt$refreshExtendedCpuClusters();
        boolean enabled = thunderbolt$getExtendedCpuClusters().stream()
                .anyMatch(cluster -> cluster.isActive() && cluster.isFastPlanningEnabled());
        ((FastCraftingControl) job).thunderbolt$setFastPlanningEnabled(enabled);
    }

    @Inject(
            method = "onServerEndTick",
            at = @At(
                    value = "FIELD",
                    target = "Lappeng/me/service/CraftingService;lastProcessedCraftingLogicChangeTick:J",
                    opcode = Opcodes.GETFIELD,
                    ordinal = 0))
    private void thunderbolt$tickExtendedCpuClusters(CallbackInfo ci) {
        thunderbolt$refreshExtendedCpuClusters();
        long latest = Long.MIN_VALUE;
        for (var cluster : thunderbolt$getExtendedCpuClusters()) {
            latest = Math.max(latest, cluster.tickCraftingLogic(
                    this.energyGrid, (CraftingService) (Object) this));
            if (cluster.consumeCpuListChanged()) {
                this.updateList = true;
            }
        }
        if (!this.thunderbolt$lastExtendedCraftingLogicChangeTickInitialized
                || latest != this.thunderbolt$lastExtendedCraftingLogicChangeTick) {
            this.thunderbolt$lastExtendedCraftingLogicChangeTickInitialized = true;
            this.thunderbolt$lastExtendedCraftingLogicChangeTick = latest;
            // Force AE2's normal waiting-key refresh without capturing its latestChange local.
            this.lastProcessedCraftingLogicChangeTick = -1L;
        }
    }

    @Inject(
            method = "onServerEndTick",
            at = @At(
                    value = "FIELD",
                    target = "Lappeng/me/service/CraftingService;interests:Lcom/google/common/collect/Multimap;",
                    opcode = Opcodes.GETFIELD,
                    ordinal = 0))
    private void thunderbolt$addExtendedWaitingKeys(CallbackInfo ci) {
        for (var cluster : thunderbolt$getExtendedCpuClusters()) {
            cluster.addWaitingKeys(this.currentlyCrafting);
        }
    }

    @Inject(method = "removeNode", at = @At("TAIL"))
    private void thunderbolt$onRemoveNode(IGridNode gridNode, CallbackInfo ci) {
        if (thunderbolt$getExtendedCpuClusterIndex().removeProvider(gridNode)) {
            thunderbolt$refreshExtendedCpuClusters();
        }
    }

    @Inject(method = "addNode", at = @At("TAIL"))
    private void thunderbolt$onAddNode(IGridNode gridNode, CompoundTag savedData, CallbackInfo ci) {
        if (thunderbolt$getExtendedCpuClusterProvider(gridNode) != null) {
            thunderbolt$getExtendedCpuClusterIndex().addProvider(gridNode);
            thunderbolt$refreshExtendedCpuClusters();
        }
    }

    @Inject(method = "updateCPUClusters", at = @At("TAIL"))
    private void thunderbolt$updateExtendedCpuClusters(CallbackInfo ci) {
        var providerNodes = new ArrayList<IGridNode>();
        for (var machineClass : this.grid.getMachineClasses()) {
            for (var node : this.grid.getMachineNodes(machineClass)) {
                if (thunderbolt$getExtendedCpuClusterProvider(node) != null) {
                    providerNodes.add(node);
                }
            }
        }
        thunderbolt$getExtendedCpuClusterIndex().replaceProviders(providerNodes);
        thunderbolt$refreshExtendedCpuClusters();
    }

    @Inject(
            method = "insertIntoCpus",
            // AdvancedAE unconditionally sets the return value from a cancellable RETURN
            // injector. A later RETURN callback is therefore skipped entirely. Mutating AE2's
            // local accumulator before IRETURN composes with both AdvancedAE and NeoECO instead.
            at = @At(value = "RETURN", shift = At.Shift.BY, by = -1),
            order = 1500)
    private void thunderbolt$insertIntoExtendedCpuClusters(
            AEKey what,
            long amount,
            Actionable type,
            CallbackInfoReturnable<Long> cir,
            @Local(ordinal = 1) LocalLongRef insertedRef) {
        long inserted = insertedRef.get();
        for (var cluster : thunderbolt$getExtendedCpuClusters()) {
            if (inserted >= amount) {
                break;
            }
            inserted += cluster.insert(what, amount - inserted, type);
        }
        insertedRef.set(inserted);
    }

    @Inject(method = "submitJob", at = @At("HEAD"), cancellable = true)
    private void thunderbolt$submitToExplicitExtendedCpuCluster(ICraftingPlan job,
                                                                 ICraftingRequester requestingMachine,
                                                                 ICraftingCPU target,
                                                                 boolean prioritizePower,
                                                                 IActionSource src,
                                                                 CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        if (job.simulation()) {
            return;
        }

        if (target instanceof ExtendedCraftingCpuCluster cluster) {
            if (!cluster.canAcceptPlan(job)) {
                cir.setReturnValue(CraftingSubmitResult.CPU_OFFLINE);
            } else {
                cir.setReturnValue(cluster.submitJob(this.grid, job, src, requestingMachine));
            }
            return;
        }

        if (target != null) {
            for (var cluster : thunderbolt$getExtendedCpuClusters()) {
                if (cluster.containsCpu(target)) {
                    cir.setReturnValue(CraftingSubmitResult.CPU_BUSY);
                    return;
                }
            }
        }

        if (thunderbolt$isPlanBound(job)) {
            if (target != null) {
                cir.setReturnValue(CraftingSubmitResult.CPU_OFFLINE);
                return;
            }
            var cluster = thunderbolt$findSuitableExtendedCpuCluster(
                    job, prioritizePower, src, new MutableObject<>());
            cir.setReturnValue(cluster != null
                    ? cluster.submitJob(this.grid, job, src, requestingMachine)
                    : CraftingSubmitResult.CPU_OFFLINE);
        }
    }

    @Inject(
            method = "submitJob",
            at = @At(
                    value = "INVOKE_ASSIGN",
                    target = "Lappeng/me/service/CraftingService;findSuitableCraftingCPU"
                            + "(Lappeng/api/networking/crafting/ICraftingPlan;"
                            + "ZLappeng/api/networking/security/IActionSource;"
                            + "Lorg/apache/commons/lang3/mutable/MutableObject;)"
                            + "Lappeng/me/cluster/implementations/CraftingCPUCluster;"),
            cancellable = true)
    private void thunderbolt$submitToAutomaticExtendedCpuCluster(
            ICraftingPlan job,
            ICraftingRequester requestingMachine,
            ICraftingCPU target,
            boolean prioritizePower,
            IActionSource src,
            CallbackInfoReturnable<ICraftingSubmitResult> cir,
            @Local CraftingCPUCluster cpuCluster,
            @Local MutableObject<UnsuitableCpus> unsuitableCpusResult) {
        if (thunderbolt$isPlanBound(job)) {
            return;
        }

        var extendedCluster = thunderbolt$findSuitableExtendedCpuCluster(
                job, prioritizePower, src, unsuitableCpusResult);
        if (extendedCluster == null) {
            return;
        }

        // AE2 has already selected the best concrete cluster. Compare that winner against the
        // best extended cluster with the same preferred/power/storage order, keeping vanilla on
        // an exact tie so installing Thunderbolt does not arbitrarily move ordinary jobs.
        if (cpuCluster == null || CraftingCpuSelectionOrder.compare(
                extendedCluster.isPreferredFor(src),
                extendedCluster.getCoProcessors(),
                extendedCluster.getAvailableStorage(),
                cpuCluster.isPreferredFor(src),
                cpuCluster.getCoProcessors(),
                cpuCluster.getAvailableStorage(),
                prioritizePower) < 0) {
            cir.setReturnValue(extendedCluster.submitJob(this.grid, job, src, requestingMachine));
        }
    }

    @Inject(
            method = "getCpus",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/google/common/collect/ImmutableSet$Builder;build()"
                            + "Lcom/google/common/collect/ImmutableSet;"))
    private void thunderbolt$getExtendedCpus(
            CallbackInfoReturnable<ImmutableSet<ICraftingCPU>> cir,
            @Local(ordinal = 0) ImmutableSet.Builder<ICraftingCPU> cpus) {
        // Mutate AE2's shared builder before it is built. Several CPU addons replace the return
        // value from cancellable RETURN injectors; a later RETURN injector is skipped as soon as
        // one of those addons calls setReturnValue(), which made extended CPUs disappear.
        for (var cluster : thunderbolt$getExtendedCpuClusters()) {
            if (!cluster.isActive()) {
                continue;
            }
            for (var cpu : cluster.getActiveCpus()) {
                cpus.add(cpu);
            }
            if (cluster.getAvailableStorage() > 0L) {
                cpus.add(cluster);
            }
        }
    }

    @Inject(method = "getRequestedAmount", at = @At("RETURN"), cancellable = true, order = 1500)
    private void thunderbolt$getExtendedRequestedAmount(AEKey what, CallbackInfoReturnable<Long> cir) {
        long requested = cir.getReturnValue();
        for (var cluster : thunderbolt$getExtendedCpuClusters()) {
            long addition = cluster.getRequestedAmount(what);
            requested = requested >= Long.MAX_VALUE - addition ? Long.MAX_VALUE : requested + addition;
        }
        cir.setReturnValue(requested);
    }

    @Inject(method = "hasCpu", at = @At("HEAD"), cancellable = true)
    private void thunderbolt$hasExtendedCpu(ICraftingCPU cpu, CallbackInfoReturnable<Boolean> cir) {
        for (var cluster : thunderbolt$getExtendedCpuClusters()) {
            if (cluster.containsCpu(cpu)) {
                cir.setReturnValue(true);
                return;
            }
        }
    }

    @Unique
    private void thunderbolt$addExtendedCpuCluster(ExtendedCraftingCpuCluster cluster) {
        cluster.prepareForCraftingService();
        cluster.restoreCraftingLinks(this::addLink);
    }

    @Unique
    private void thunderbolt$refreshExtendedCpuClusters() {
        boolean changed = thunderbolt$getExtendedCpuClusterIndex().refresh(
                ExtendedCraftingCpuServiceMixin::thunderbolt$resolveExtendedCpuCluster,
                this::thunderbolt$addExtendedCpuCluster);
        if (changed) {
            this.updateList = true;
        }
    }

    @Unique
    private DynamicCraftingCpuClusterIndex<IGridNode, ExtendedCraftingCpuCluster>
            thunderbolt$getExtendedCpuClusterIndex() {
        if (this.thunderbolt$extendedCpuClusterIndex == null) {
            this.thunderbolt$extendedCpuClusterIndex = new DynamicCraftingCpuClusterIndex<>();
        }
        return this.thunderbolt$extendedCpuClusterIndex;
    }

    @Unique
    private Set<ExtendedCraftingCpuCluster> thunderbolt$getExtendedCpuClusters() {
        return thunderbolt$getExtendedCpuClusterIndex().clusters();
    }

    @Unique
    @Nullable
    private static ExtendedCraftingCpuClusterProvider thunderbolt$getExtendedCpuClusterProvider(IGridNode node) {
        var service = node.getService(ExtendedCraftingCpuClusterProvider.class);
        if (service != null) {
            return service;
        }
        return node.getOwner() instanceof ExtendedCraftingCpuClusterProvider provider ? provider : null;
    }

    @Unique
    @Nullable
    private static ExtendedCraftingCpuCluster thunderbolt$resolveExtendedCpuCluster(IGridNode node) {
        var provider = thunderbolt$getExtendedCpuClusterProvider(node);
        return provider != null ? provider.getExtendedCraftingCpuCluster() : null;
    }

    @Unique
    @Nullable
    private ExtendedCraftingCpuCluster thunderbolt$findSuitableExtendedCpuCluster(
            ICraftingPlan job,
            boolean prioritizePower,
            IActionSource src,
            MutableObject<UnsuitableCpus> unsuitableCpusResult) {
        var clusters = thunderbolt$getExtendedCpuClusters();
        var valid = new ArrayList<ExtendedCraftingCpuCluster>(clusters.size());
        int offline = 0;
        int busy = 0;
        int tooSmall = 0;
        int excluded = 0;
        for (var cluster : clusters) {
            if (!cluster.isActive()) {
                offline++;
                continue;
            }
            if (cluster.isBusy()) {
                busy++;
                continue;
            }
            if (cluster.getAvailableStorage() < job.bytes()) {
                tooSmall++;
                continue;
            }
            if (!cluster.canAcceptPlan(job)) {
                excluded++;
                continue;
            }
            if (!cluster.canBeAutoSelectedFor(src)) {
                excluded++;
                continue;
            }
            valid.add(cluster);
        }

        if (valid.isEmpty()) {
            if (offline > 0 || busy > 0 || tooSmall > 0 || excluded > 0) {
                var existing = unsuitableCpusResult.getValue();
                if (existing == null) {
                    unsuitableCpusResult.setValue(new UnsuitableCpus(offline, busy, tooSmall, excluded));
                } else {
                    unsuitableCpusResult.setValue(new UnsuitableCpus(
                            saturatingAdd(existing.offline(), offline),
                            saturatingAdd(existing.busy(), busy),
                            saturatingAdd(existing.tooSmall(), tooSmall),
                            saturatingAdd(existing.excluded(), excluded)));
                }
            }
            return null;
        }

        valid.sort((a, b) -> CraftingCpuSelectionOrder.compare(
                a.isPreferredFor(src),
                a.getCoProcessors(),
                a.getAvailableStorage(),
                b.isPreferredFor(src),
                b.getCoProcessors(),
                b.getAvailableStorage(),
                prioritizePower));
        return valid.getFirst();
    }

    @Unique
    private static boolean thunderbolt$isPlanBound(ICraftingPlan job) {
        return job instanceof LoopCraftingPlan;
    }

    @Unique
    private static int saturatingAdd(int left, int right) {
        return left >= Integer.MAX_VALUE - right ? Integer.MAX_VALUE : left + right;
    }
}
