package com.moakiee.ae2lt.crafting.runtime;

import java.util.Collection;
import java.util.Set;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.crafting.CraftingPlan;
import appeng.crafting.CraftingLink;
import appeng.me.service.CraftingService;

/**
 * Public service adapter for crafting CPU implementations that are not backed by AE2's concrete
 * {@code CraftingCPUCluster} class.
 *
 * <p>An implementation represents one physical/shared-capacity cluster. It may expose multiple
 * active {@link ICraftingCPU} instances, with one instance per independently visible crafting job.
 * Thunderbolt adds these clusters to the normal AE2 crafting-service lifecycle without requiring
 * the implementation to patch {@link CraftingService} itself.
 */
public interface ExtendedCraftingCpuCluster extends ICraftingCPU {
    /** Whether the physical cluster is currently online and able to craft. */
    boolean isActive();

    /** Whether this active cluster opts its ME network into Thunderbolt's fast planner. */
    default boolean isFastPlanningEnabled() {
        return false;
    }

    /** Independently visible CPUs/jobs currently owned by this cluster. */
    Collection<? extends ICraftingCPU> getActiveCpus();

    /** Advances all active crafting logic and returns its latest waiting-key change marker. */
    long tickCraftingLogic(IEnergyService energyService, CraftingService craftingService);

    /** Adds every key currently awaited by this cluster. */
    void addWaitingKeys(Set<AEKey> waitingKeys);

    /** Offers returned crafting output to the active CPUs in this cluster. */
    long insert(AEKey what, long amount, Actionable mode);

    /** Returns the aggregate amount of {@code what} awaited by active CPUs. */
    long getRequestedAmount(AEKey what);

    /** Submits a new job using capacity owned by this cluster. */
    ICraftingSubmitResult submitJob(
            IGrid grid,
            ICraftingPlan plan,
            IActionSource src,
            @Nullable ICraftingRequester requester);

    /**
     * Called before restored links are collected after the cluster joins the service.
     * Implementations must be idempotent because AE2 rebuilds its CPU list repeatedly.
     */
    default void prepareForCraftingService() {
    }

    /** Supplies all persisted crafting links owned by active CPUs. */
    default void restoreCraftingLinks(Consumer<CraftingLink> consumer) {
    }

    /** Whether this cluster accepts the plan in addition to normal capacity/selection checks. */
    default boolean canAcceptPlan(ICraftingPlan plan) {
        return plan instanceof CraftingPlan;
    }

    /** Mirrors AE2's automatic CPU-selection rules for a non-vanilla cluster. */
    default boolean canBeAutoSelectedFor(IActionSource source) {
        return switch (getSelectionMode()) {
            case ANY -> true;
            case PLAYER_ONLY -> source.player().isPresent();
            case MACHINE_ONLY -> source.player().isEmpty();
        };
    }

    /** Mirrors AE2's preferred-source ordering for automatic CPU selection. */
    default boolean isPreferredFor(IActionSource source) {
        return switch (getSelectionMode()) {
            case ANY -> false;
            case PLAYER_ONLY -> source.player().isPresent();
            case MACHINE_ONLY -> source.player().isEmpty();
        };
    }

    /** Signals that the visible CPU list must be rebuilt. */
    default boolean consumeCpuListChanged() {
        return false;
    }

    /** Tests both the shared-capacity submission target and its active child CPUs. */
    default boolean containsCpu(ICraftingCPU cpu) {
        if (cpu == this) {
            return true;
        }
        for (var activeCpu : getActiveCpus()) {
            if (activeCpu == cpu) {
                return true;
            }
        }
        return false;
    }
}
