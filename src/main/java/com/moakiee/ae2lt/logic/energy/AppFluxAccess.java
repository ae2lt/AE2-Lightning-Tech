package com.moakiee.ae2lt.logic.energy;

import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageCells;

import com.glodblock.github.appflux.api.IFluxCell;
import com.glodblock.github.appflux.common.me.cell.FluxCellInventory;
import com.glodblock.github.appflux.common.me.key.FluxKey;
import com.glodblock.github.appflux.common.me.key.type.EnergyType;
import com.glodblock.github.appflux.config.AFConfig;

import mekanism.api.Action;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.api.math.FloatingLong;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.config.MekanismConfig;
import mekanism.common.util.UnitDisplayUtils;
import sonar.fluxnetworks.api.FluxCapabilities;
import sonar.fluxnetworks.api.energy.IFNEnergyStorage;

/**
 * Direct access to Applied Flux APIs. This class is ONLY loaded when AppFlux
 * is present at runtime — the JVM's lazy class loading ensures that importing
 * AppFlux types here never triggers a {@link NoClassDefFoundError} as long as
 * callers guard access behind the {@code LOADED} check in
 * {@link AppFluxBridge}.
 */
final class AppFluxAccess {

    static final AEKey FE_KEY = FluxKey.of(EnergyType.FE);

    static final long TRANSFER_RATE;
    private static final boolean FLUX_NETWORKS_LOADED;
    private static final boolean MEKANISM_LOADED;

    static {
        long rate = AFConfig.getFluxAccessorIO();
        TRANSFER_RATE = rate == 0L ? Long.MAX_VALUE : Math.max(0L, rate);
        FLUX_NETWORKS_LOADED = isClassPresent("sonar.fluxnetworks.api.energy.IFNEnergyStorage");
        MEKANISM_LOADED = isClassPresent("mekanism.common.capabilities.Capabilities");
    }

    static boolean isFluxCell(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof IFluxCell;
    }

    @Nullable
    static Object createCapCache(ServerLevel level, BlockPos pos,
                                 Supplier<IGrid> gridSupplier) {
        return new CapabilityTargetCache(level, pos);
    }

    @Nullable
    static TargetAccess resolveEnergyTarget(Object energyCapCache, Direction side) {
        if (!(energyCapCache instanceof CapabilityTargetCache cache)) {
            return null;
        }

        TargetAccess target = resolveFluxNetworkTarget(cache, side);
        if (target != null) {
            return target;
        }
        target = resolveMekanismTarget(cache, side);
        if (target != null) {
            return target;
        }
        return ForgeEnergyTarget.resolve(cache, side);
    }

    static long simulateTarget(@Nullable TargetAccess access, long maxFe) {
        return access != null && maxFe > 0L
                ? Math.max(0L, access.simulateReceive(maxFe))
                : 0L;
    }

    static long sendToTarget(@Nullable TargetAccess access, IStorageService storage,
                             IActionSource source, long maxFe) {
        if (access == null || maxFe <= 0L) {
            return 0L;
        }

        long requested = Math.max(0L, access.simulateReceive(maxFe));
        if (requested <= 0L) {
            return 0L;
        }

        long extracted = storage.getInventory().extract(
                FE_KEY, Math.min(requested, maxFe), Actionable.MODULATE, source);
        if (extracted <= 0L) {
            return 0L;
        }

        long accepted = Math.min(extracted, Math.max(0L, access.receive(extracted)));
        long leftover = extracted - accepted;
        if (leftover > 0L) {
            storage.getInventory().insert(FE_KEY, leftover, Actionable.MODULATE, source);
        }
        return accepted;
    }

    static long sendToTargetKnownDemand(@Nullable TargetAccess access, IStorageService storage,
                                        IActionSource source, long requested) {
        if (access == null || requested <= 0L) {
            return 0L;
        }

        long extracted = storage.getInventory().extract(
                FE_KEY, requested, Actionable.MODULATE, source);
        if (extracted <= 0L) {
            return 0L;
        }

        long accepted = Math.min(extracted, Math.max(0L, access.receive(extracted)));
        long leftover = extracted - accepted;
        if (leftover > 0L) {
            storage.getInventory().insert(FE_KEY, leftover, Actionable.MODULATE, source);
        }
        return accepted;
    }

    /**
     * Direct-buffer variant of {@link #sendToTargetKnownDemand(TargetAccess, IStorageService, IActionSource, long)}
     * for callers that already hold a {@link BufferedMEStorage} reference (the
     * NORMAL-mode tickNormal hot path). Skips the {@code IStorageService.getInventory()}
     * interface dispatch — JIT cannot always devirt that across modules and
     * each NORMAL modulate-pass iteration would otherwise spend ~5 ns there.
     */
    static long sendToTargetKnownDemand(@Nullable TargetAccess access, BufferedMEStorage buffer,
                                        IActionSource source, long requested) {
        if (access == null || requested <= 0L) {
            return 0L;
        }

        long extracted = buffer.extract(FE_KEY, requested, Actionable.MODULATE, source);
        if (extracted <= 0L) {
            return 0L;
        }

        long accepted = Math.min(extracted, Math.max(0L, access.receive(extracted)));
        long leftover = extracted - accepted;
        if (leftover > 0L) {
            buffer.insert(FE_KEY, leftover, Actionable.MODULATE, source);
        }
        return accepted;
    }

    static long sendToTargetRepeatedOptimistic(@Nullable TargetAccess access, BufferedMEStorage buffer,
                                              IActionSource source, long maxFe, int maxCalls) {
        if (access == null || maxFe <= 0L || maxCalls <= 0) {
            return 0L;
        }

        long totalAccepted = 0L;
        long remaining = 0L;
        try {
            for (int i = 0; i < maxCalls; i++) {
                if (remaining <= 0L) {
                    remaining = extractRepeatedBudget(access, buffer, source, maxFe, maxCalls - i);
                    if (remaining <= 0L) {
                        break;
                    }
                }

                long attempt = remaining < maxFe ? remaining : maxFe;
                long accepted = access.receive(attempt);
                if (accepted <= 0L) {
                    break;
                }
                if (accepted > attempt) {
                    accepted = attempt;
                }

                remaining -= accepted;
                totalAccepted = saturatingAdd(totalAccepted, accepted);
            }
        } finally {
            if (remaining > 0L) {
                buffer.returnFromDirectSend(remaining, source);
            }
        }
        return totalAccepted;
    }

    private static long extractRepeatedBudget(TargetAccess access, BufferedMEStorage buffer,
                                             IActionSource source, long maxFe, int remainingCalls) {
        long budget = saturatingMul(maxFe, remainingCalls);
        long extracted = buffer.extractForDirectSend(budget, source);
        if (extracted > 0L) {
            return extracted;
        }

        long simulated = access.simulateReceive(maxFe);
        long currentDemand = simulated < maxFe ? simulated : maxFe;
        if (currentDemand <= 0L) {
            return 0L;
        }
        long refillDemand = buffer.refillBudgetForDirectSend(currentDemand);
        if (refillDemand <= 0L) {
            return 0L;
        }
        buffer.refillForDirectSend(refillDemand, source);
        return buffer.extractForDirectSend(budget, source);
    }

    static long getFluxCellCapacity(ItemStack stack) {
        var inventory = StorageCells.getCellInventory(stack, null);
        if (inventory instanceof FluxCellInventory fluxInv) {
            return Math.max(0L, fluxInv.getMaxEnergy());
        }
        if (stack.getItem() instanceof IFluxCell fluxCell) {
            return Math.max(0L, fluxCell.getBytes(stack));
        }
        return 0L;
    }

    static void persistCellStorage(@Nullable MEStorage storage) {
        if (storage instanceof FluxCellInventory fluxInv) {
            fluxInv.persist();
        }
    }

    private AppFluxAccess() {}

    @Nullable
    private static TargetAccess resolveFluxNetworkTarget(CapabilityTargetCache cache, Direction side) {
        if (!FLUX_NETWORKS_LOADED) {
            return null;
        }
        try {
            IFNEnergyStorage target = getTargetCapability(cache, side, FluxCapabilities.FN_ENERGY_STORAGE);
            return target != null ? new FluxNetworkTarget(target) : null;
        } catch (LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    private static TargetAccess resolveMekanismTarget(CapabilityTargetCache cache, Direction side) {
        if (!MEKANISM_LOADED) {
            return null;
        }
        try {
            IStrictEnergyHandler target = getTargetCapability(cache, side, Capabilities.STRICT_ENERGY);
            return target != null ? MekanismStrictTarget.create(target) : null;
        } catch (LinkageError ignored) {
            return null;
        }
    }

    private record FluxNetworkTarget(IFNEnergyStorage target) implements TargetAccess {
        @Override
        public long simulateReceive(long maxFe) {
            return Math.max(0L, target.receiveEnergyL(maxFe, true));
        }

        @Override
        public long receive(long amountFe) {
            return Math.max(0L, target.receiveEnergyL(amountFe, false));
        }
    }

    private static final class MekanismStrictTarget implements TargetAccess {
        private final IStrictEnergyHandler target;
        private final FloatingLong maxJoulesPerCall;
        private final long maxForgeEnergyPerCall;

        private MekanismStrictTarget(IStrictEnergyHandler target, FloatingLong maxJoulesPerCall,
                                     long maxForgeEnergyPerCall) {
            this.target = target;
            this.maxJoulesPerCall = maxJoulesPerCall;
            this.maxForgeEnergyPerCall = maxForgeEnergyPerCall;
        }

        static MekanismStrictTarget create(IStrictEnergyHandler target) {
            double rate = MekanismConfig.general.forgeConversionRate.get().doubleValue();
            long maxFe = maxForgeEnergyFor(rate);
            long cappedTransfer = TRANSFER_RATE > 0L ? Math.min(TRANSFER_RATE, maxFe) : 0L;
            FloatingLong maxJ = cappedTransfer > 0L
                    ? UnitDisplayUtils.EnergyUnit.FORGE_ENERGY.convertFrom(cappedTransfer)
                    : FloatingLong.ZERO;
            return new MekanismStrictTarget(target, maxJ, maxFe);
        }

        @Override
        public long simulateReceive(long maxFe) {
            return insert(maxFe, Action.SIMULATE);
        }

        @Override
        public long receive(long amountFe) {
            return insert(amountFe, Action.EXECUTE);
        }

        private long insert(long amountFe, Action action) {
            if (amountFe <= 0L) {
                return 0L;
            }
            long effectiveAmountFe = Math.min(amountFe, maxForgeEnergyPerCall);
            if (effectiveAmountFe <= 0L) {
                return 0L;
            }
            FloatingLong mekanismAmount = amountFe == TRANSFER_RATE
                    ? maxJoulesPerCall
                    : UnitDisplayUtils.EnergyUnit.FORGE_ENERGY.convertFrom(effectiveAmountFe);
            if (mekanismAmount.isZero()) {
                return 0L;
            }
            FloatingLong remainder = target.insertEnergy(mekanismAmount, action);
            if (remainder.isZero()) {
                return effectiveAmountFe;
            }
            if (remainder.greaterOrEqual(mekanismAmount)) {
                return 0L;
            }
            FloatingLong acceptedJ = mekanismAmount.subtract(remainder);
            return Math.min(
                    effectiveAmountFe,
                    UnitDisplayUtils.EnergyUnit.FORGE_ENERGY.convertToAsLong(acceptedJ));
        }
    }

    private record ForgeEnergyTarget(IEnergyStorage target) implements TargetAccess {
        @Nullable
        static TargetAccess resolve(CapabilityTargetCache cache, Direction side) {
            IEnergyStorage target = getTargetCapability(cache, side, ForgeCapabilities.ENERGY);
            return target != null ? new ForgeEnergyTarget(target) : null;
        }

        @Override
        public long simulateReceive(long maxFe) {
            return Math.max(0, target.receiveEnergy(clampToInt(maxFe), true));
        }

        @Override
        public long receive(long amountFe) {
            return Math.max(0, target.receiveEnergy(clampToInt(amountFe), false));
        }
    }

    private record CapabilityTargetCache(ServerLevel level, BlockPos pos) {
        private CapabilityTargetCache {
            pos = pos.immutable();
        }

        @Nullable
        private BlockEntity resolveTarget(Direction side) {
            BlockPos targetPos = pos.relative(side);
            return level.isLoaded(targetPos) ? level.getBlockEntity(targetPos) : null;
        }
    }

    @Nullable
    private static <T> T getTargetCapability(CapabilityTargetCache cache, Direction side, Capability<T> capability) {
        BlockEntity target = cache.resolveTarget(side);
        if (target == null) {
            return null;
        }
        return target.getCapability(capability, side.getOpposite()).resolve().orElse(null);
    }

    private static int clampToInt(long value) {
        return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0L, value);
    }

    private static long clampToLong(double value) {
        if (Double.isNaN(value) || value <= 0.0) {
            return 0L;
        }
        if (value >= 9.223372036854776E18) {
            return Long.MAX_VALUE;
        }
        return (long) value;
    }

    private static long maxForgeEnergyFor(double feToJoules) {
        if (Double.isNaN(feToJoules) || feToJoules <= 0.0) {
            return Long.MAX_VALUE;
        }
        double max = 9.223372036854776E18 / feToJoules;
        if (max >= 9.223372036854776E18) {
            return Long.MAX_VALUE;
        }
        return Math.max(1L, (long) max);
    }

    private static long saturatingAdd(long a, long b) {
        long r = a + b;
        return ((a ^ r) & (b ^ r)) < 0L ? Long.MAX_VALUE : r;
    }

    private static long saturatingMul(long a, long b) {
        if (a <= 0L || b <= 0L) {
            return 0L;
        }
        return a > Long.MAX_VALUE / b ? Long.MAX_VALUE : a * b;
    }

    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, AppFluxAccess.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }
}

