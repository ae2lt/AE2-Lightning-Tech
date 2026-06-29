package com.moakiee.ae2lt.mixin;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.world.level.Level;
import net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPU;
import net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic;
import net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.hooks.ticking.TickHandler;
import appeng.me.service.CraftingService;

import com.moakiee.ae2lt.logic.batch.AaeBatchJobView;
import com.moakiee.ae2lt.logic.batch.BatchExecutor;
import com.moakiee.ae2lt.logic.batch.BatchProviderFilterIterable;

@Pseudo
@Mixin(value = AdvCraftingCPULogic.class, remap = false)
public abstract class AdvCraftingCpuLogicBatchMixin {
    @Shadow
    private ExecutingCraftingJob job;

    @Shadow
    @Final
    private ListCraftingInventory inventory;

    @Shadow
    @Final
    AdvCraftingCPU cpu;

    @Unique
    private final Map<IPatternDetails, IdentityHashMap<ICraftingProvider, Boolean>> ae2lt$batchedByTask =
            new HashMap<>();

    @Unique
    private long ae2lt$batchTick = Long.MIN_VALUE;

    @Unique
    private boolean ae2lt$batchExhaustedThisTick;

    @WrapOperation(
            method = "tickCraftingLogic",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/pedroksl/advanced_ae/common/logic/AdvCraftingCPULogic;executeCrafting"
                            + "(ILappeng/me/service/CraftingService;Lappeng/api/networking/energy/IEnergyService;"
                            + "Lnet/minecraft/world/level/Level;)I"
            )
    )
    private int ae2lt$wrapExecuteCrafting(AdvCraftingCPULogic self,
                                          int remainingOps,
                                          CraftingService craftingService,
                                          IEnergyService energyService,
                                          Level level,
                                          Operation<Integer> original) {
        long now = TickHandler.instance().getCurrentTick();
        if (now != ae2lt$batchTick) {
            ae2lt$batchTick = now;
            ae2lt$batchedByTask.clear();
            ae2lt$batchExhaustedThisTick = false;
        }

        if (job == null || ae2lt$batchExhaustedThisTick) {
            return original.call(self, remainingOps, craftingService, energyService, level);
        }

        var batchResult = BatchExecutor.runBatchOnly(
                remainingOps,
                craftingService,
                energyService,
                level,
                new AaeBatchJobView(job),
                inventory,
                ae2lt$batchedByTask,
                cpu::markDirty);

        if (batchResult.dispatchedCopies() > 0) {
            // Batch providers supply the internal parallel capacity, while the CPU pays dispatch pressure:
            // each pushBatch dispatch of d copies costs ceil(sqrt(d)) ops, summed across this tick's
            // dispatches. The ops budget caps the running sum, so a tick never exceeds its rolling usedOps.
            return batchResult.consumedCpuOps();
        }

        // No batch-dispatchable task this tick (no batch provider / all full / out of material).
        // Game time is frozen within a tick, so capacity cannot recover; skip the per-round re-probe.
        ae2lt$batchExhaustedThisTick = true;
        return original.call(self, remainingOps, craftingService, energyService, level);
    }

    @WrapOperation(
            method = "executeCrafting",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/me/service/CraftingService;getProviders"
                            + "(Lappeng/api/crafting/IPatternDetails;)Ljava/lang/Iterable;"
            )
    )
    private Iterable<ICraftingProvider> ae2lt$filterBatched(CraftingService craftingService,
                                                            IPatternDetails details,
                                                            Operation<Iterable<ICraftingProvider>> original) {
        var raw = original.call(craftingService, details);
        if (ae2lt$batchedByTask.isEmpty()) return raw;
        var perTask = ae2lt$batchedByTask.get(details);
        if (perTask == null || perTask.isEmpty()) return raw;
        return new BatchProviderFilterIterable(raw, perTask);
    }
}
