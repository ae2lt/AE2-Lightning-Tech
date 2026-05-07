package com.moakiee.ae2lt.logic.energy;

import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;

/**
 * Temporary AppFlux shim for the 26.1.2 port.
 *
 * <p>Applied Flux and its optional energy target APIs do not currently have
 * 26.1.2 compile artifacts in this workspace, so the direct integration is
 * disabled until those dependencies can be restored.
 */
final class AppFluxAccess {
    static final AEKey FE_KEY = null;
    static final long TRANSFER_RATE = 0L;

    private AppFluxAccess() {
    }

    static boolean isFluxCell(ItemStack stack) {
        return false;
    }

    @Nullable
    static Object createCapCache(ServerLevel level, BlockPos pos, Supplier<IGrid> gridSupplier) {
        return null;
    }

    @Nullable
    static TargetAccess resolveEnergyTarget(Object energyCapCache, Direction side) {
        return null;
    }

    static long simulateTarget(@Nullable TargetAccess access, long maxFe) {
        return 0L;
    }

    static long sendToTarget(@Nullable TargetAccess access, IStorageService storage,
                             IActionSource source, long maxFe) {
        return 0L;
    }

    static long sendToTargetKnownDemand(@Nullable TargetAccess access, IStorageService storage,
                                        IActionSource source, long requested) {
        return 0L;
    }

    static long sendToTargetKnownDemand(@Nullable TargetAccess access, BufferedMEStorage buffer,
                                        IActionSource source, long requested) {
        return 0L;
    }

    static long sendToTargetRepeatedOptimistic(@Nullable TargetAccess access, BufferedMEStorage buffer,
                                               IActionSource source, long maxFe, int maxCalls) {
        return 0L;
    }

    static long getFluxCellCapacity(ItemStack stack) {
        return 0L;
    }

    static void persistCellStorage(@Nullable MEStorage storage) {
    }
}
