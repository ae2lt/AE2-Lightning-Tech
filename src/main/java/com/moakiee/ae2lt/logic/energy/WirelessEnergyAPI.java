package com.moakiee.ae2lt.logic.energy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;

/**
 * High-level helpers for wireless FE distribution through Applied Flux.
 *
 * Applied Flux's {@code EnergyCapCache} expects to be constructed with a
 * "host" block position and then resolves the actual target via
 * {@code pos.relative(side)} (using {@code side.getOpposite()} as the cap
 * side context). Since our wireless connections store the target block's own
 * position and the face the player clicked, we emulate a virtual host sitting
 * one block beyond the target in the direction of the clicked face.
 */
public final class WirelessEnergyAPI {

    private WirelessEnergyAPI() {}

    public record Target(ResourceKey<Level> dimension, BlockPos pos, Direction face) {
        public Target {
            pos = pos.immutable();
        }

        /** Position of the virtual accessor that sits next to the target block. */
        public BlockPos virtualHostPos() {
            return pos.relative(face);
        }

        /** Direction from the virtual host toward the target block. */
        public Direction hostSide() {
            return face.getOpposite();
        }
    }

    private record ResolvedTarget(ServerLevel level, Target target) {
    }

    @Nullable
    public static ServerLevel resolveLevel(MinecraftServer server, Target target) {
        ServerLevel level = server.getLevel(target.dimension());
        if (level == null || !level.isLoaded(target.pos())) {
            return null;
        }
        return level;
    }

    @Nullable
    public static Object resolveCapCache(ServerLevel providerLevel, Target target, Supplier<IGrid> gridSupplier) {
        ServerLevel targetLevel = resolveLevel(providerLevel.getServer(), target);
        return targetLevel != null
                ? AppFluxBridge.createCapCache(targetLevel, target.virtualHostPos(), gridSupplier)
                : null;
    }

    @Nullable
    public static TargetAccess resolveEnergyTarget(@Nullable Object capCache, Direction targetFace) {
        return AppFluxBridge.resolveEnergyTarget(capCache, targetFace.getOpposite());
    }

    public static long simulateTarget(@Nullable TargetAccess target, long maxFe) {
        return AppFluxBridge.simulateTarget(target, maxFe);
    }

    public static long sendToTarget(@Nullable TargetAccess target, IStorageService storage,
                                    IActionSource source, long maxFe) {
        return AppFluxBridge.sendToTarget(target, storage, source, maxFe);
    }

    public static long sendToTargetKnownDemand(@Nullable TargetAccess target, IStorageService storage,
                                               IActionSource source, long requested) {
        return AppFluxBridge.sendToTargetKnownDemand(target, storage, source, requested);
    }

    public static long sendToTargetRepeatedOptimistic(@Nullable TargetAccess target, BufferedMEStorage buffer,
                                                      IActionSource source, long maxFe, int maxCalls) {
        return AppFluxBridge.sendToTargetRepeatedOptimistic(target, buffer, source, maxFe, maxCalls);
    }

    public static long distributeBatch(
            ServerLevel providerLevel,
            List<Target> targets,
            BufferedMEStorage buffered,
            BufferedStorageService proxy,
            Supplier<IGrid> gridSupplier,
            IActionSource source) {
        if (!AppFluxBridge.canUseEnergyHandler() || AppFluxBridge.FE_KEY == null || targets.isEmpty()) {
            return 0L;
        }

        List<ResolvedTarget> liveTargets = new ArrayList<>(targets.size());
        for (Target target : targets) {
            ServerLevel level = resolveLevel(providerLevel.getServer(), target);
            if (level != null) {
                liveTargets.add(new ResolvedTarget(level, target));
            }
        }

        if (liveTargets.isEmpty()) {
            return 0L;
        }

        buffered.setCostMultiplier(1);

        long totalPushed = 0L;
        for (ResolvedTarget entry : liveTargets) {
            Object capCache = AppFluxBridge.createCapCache(
                    entry.level(), entry.target().virtualHostPos(), gridSupplier);
            TargetAccess target = resolveEnergyTarget(capCache, entry.target().face());
            totalPushed += sendToTarget(target, proxy, source, AppFluxBridge.TRANSFER_RATE);
        }

        return totalPushed;
    }
}
