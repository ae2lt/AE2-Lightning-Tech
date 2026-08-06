package com.moakiee.ae2lt.logic.energy;

import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageCells;

import com.glodblock.github.appflux.api.IFluxCell;
import com.glodblock.github.appflux.common.me.cell.FluxCellInventory;
import com.glodblock.github.appflux.common.me.energy.EnergyCapCache;
import com.glodblock.github.appflux.common.me.key.FluxKey;
import com.glodblock.github.appflux.common.me.key.type.EnergyType;
import com.glodblock.github.appflux.config.AFConfig;
import com.glodblock.github.appflux.xmod.fluxnetwork.FluxNetworkCap;
import com.glodblock.github.appflux.xmod.mek.MekEnergyCap;
import com.glodblock.github.appflux.xmod.mi.LongEnergyCap;

import dev.technici4n.grandpower.api.ILongEnergyStorage;
import mekanism.api.Action;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.common.util.UnitDisplayUtils;
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
    private static final boolean GRAND_POWER_LOADED;
    private static final boolean FLUX_NETWORKS_LOADED;
    private static final boolean MEKANISM_LOADED;

    static {
        long rate = AFConfig.getFluxAccessorIO();
        TRANSFER_RATE = rate == 0L ? Long.MAX_VALUE : Math.max(0L, rate);
        GRAND_POWER_LOADED = isClassPresent("dev.technici4n.grandpower.api.ILongEnergyStorage");
        FLUX_NETWORKS_LOADED = isClassPresent("sonar.fluxnetworks.api.energy.IFNEnergyStorage");
        MEKANISM_LOADED = isClassPresent("mekanism.api.energy.IStrictEnergyHandler");
    }

    static boolean isFluxCell(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof IFluxCell;
    }

    @Nullable
    static Object createCapCache(ServerLevel level, BlockPos pos,
                                 Supplier<IGrid> gridSupplier) {
        return new EnergyCapCache(level, pos, gridSupplier);
    }

    @Nullable
    static TargetAccess resolveEnergyTarget(Object energyCapCache, Direction side) {
        if (!(energyCapCache instanceof EnergyCapCache cache)) {
            return null;
        }

        TargetAccess target = resolveGrandPowerTarget(cache, side);
        if (target != null) {
            return target;
        }
        target = resolveFluxNetworkTarget(cache, side);
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
    private static TargetAccess resolveGrandPowerTarget(EnergyCapCache cache, Direction side) {
        if (!GRAND_POWER_LOADED) {
            return null;
        }
        try {
            ILongEnergyStorage target = cache.getEnergyCap(LongEnergyCap.CAP, side);
            return target != null ? new GrandPowerTarget(target) : null;
        } catch (LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    private static TargetAccess resolveFluxNetworkTarget(EnergyCapCache cache, Direction side) {
        if (!FLUX_NETWORKS_LOADED) {
            return null;
        }
        try {
            IFNEnergyStorage target = cache.getEnergyCap(FluxNetworkCap.CAP, side);
            return target != null ? new FluxNetworkTarget(target) : null;
        } catch (LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    private static TargetAccess resolveMekanismTarget(EnergyCapCache cache, Direction side) {
        if (!MEKANISM_LOADED) {
            return null;
        }
        try {
            IStrictEnergyHandler target = cache.getEnergyCap(MekEnergyCap.CAP, side);
            return target != null ? MekanismStrictTarget.create(target) : null;
        } catch (LinkageError ignored) {
            return null;
        }
    }

    private record GrandPowerTarget(ILongEnergyStorage target) implements TargetAccess {
        @Override
        public long simulateReceive(long maxFe) {
            return Math.max(0L, target.receive(maxFe, true));
        }

        @Override
        public long receive(long amountFe) {
            return Math.max(0L, target.receive(amountFe, false));
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

    /**
     * Mekanism IStrictEnergyHandler facade.
     *
     * <p>Caches the FE↔Joule conversion rate at construction so the hot path
     * does not re-query {@code MekanismConfig.general.forgeConversionRate} on
     * every receive call. Cap invalidation rebuilds the target instance, which
     * picks up any in-game config reload at the next resolve. Stale rates only
     * persist between cap invalidations on a single block — well within
     * acceptable bounds for a per-tick energy throughput hot path.
     *
     * <p>Mekanism's stock conversion is exactly {@code 1 FE = 2.5 J = 5/2 J},
     * so when no config override is in play we replace
     * {@code clampToLong((double) x * feToJoules)} with the equivalent
     * pure-integer {@code (x * 5L) >> 1}, and similarly J→FE becomes
     * {@code (x << 1) / 5L}. Both directions become a single imul plus a
     * shift / constant-divisor (HotSpot lowers {@code / 5L} to a magic-number
     * high-mul on x64), saving the long→double round-trip, the double-mul,
     * the {@code clampToLong} NaN/range branch tower, and all FPU register
     * pressure. Custom conversion rates fall back to the original double
     * path automatically.
     *
     * <p>Also short-circuits the J→FE back-conversion when the target accepts
     * the full amount (the dominant case in OVERLOAD): when {@code remainder}
     * is zero we simply return the original {@code amountFe} unchanged,
     * skipping the conversion entirely.
     */
    private static final class MekanismStrictTarget implements TargetAccess {
        /** Mekanism stock {@code joulesPerForgeEnergy}. */
        private static final double DEFAULT_FE_TO_JOULES = 2.5;
        /** Largest {@code amountFe} that lets {@code amountFe * 5L} stay
         *  inside {@code Long}. Real machine demands per tick never come
         *  near this — it exists purely so a misconfigured TRANSFER_RATE
         *  cannot wrap silently. */
        private static final long FE_FAST_PATH_LIMIT = Long.MAX_VALUE / 5L;
        /** Largest {@code acceptedJ} that lets {@code acceptedJ << 1} stay
         *  inside {@code Long}. Same rationale as above. */
        private static final long J_FAST_PATH_LIMIT = Long.MAX_VALUE >> 1;

        private final IStrictEnergyHandler target;
        private final double feToJoules;
        private final double joulesToFe;
        /** Pre-computed Joule equivalent of {@link #TRANSFER_RATE} (the per-call cap). */
        private final long maxJoulesPerCall;
        /** Largest FE request this target can convert without clamping Joules. */
        private final long maxForgeEnergyPerCall;
        /** True iff Mekanism is running with the stock 5/2 conversion ratio. */
        private final boolean defaultConversion;

        private MekanismStrictTarget(IStrictEnergyHandler target, double feToJoules,
                                     double joulesToFe, long maxJoulesPerCall,
                                     long maxForgeEnergyPerCall) {
            this.target = target;
            this.feToJoules = feToJoules;
            this.joulesToFe = joulesToFe;
            this.maxJoulesPerCall = maxJoulesPerCall;
            this.maxForgeEnergyPerCall = maxForgeEnergyPerCall;
            this.defaultConversion = feToJoules == DEFAULT_FE_TO_JOULES;
        }

        static MekanismStrictTarget create(IStrictEnergyHandler target) {
            double rate = UnitDisplayUtils.EnergyUnit.FORGE_ENERGY.getConversion();
            double inverse = rate > 0.0 ? 1.0 / rate : 1.0;
            long maxFe = maxForgeEnergyFor(rate);
            long cappedTransfer = TRANSFER_RATE > 0L ? Math.min(TRANSFER_RATE, maxFe) : 0L;
            long maxJ = cappedTransfer > 0L ? clampToLong((double) cappedTransfer * rate) : 0L;
            return new MekanismStrictTarget(target, rate, inverse, maxJ, maxFe);
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
            long mekanismAmount;
            if (amountFe == TRANSFER_RATE) {
                // Per-call cap: pre-computed at construction, no math at all.
                mekanismAmount = maxJoulesPerCall;
            } else if (defaultConversion && effectiveAmountFe <= FE_FAST_PATH_LIMIT) {
                // 1 FE = 5/2 J → integer-only: imul + shift, no FPU.
                mekanismAmount = (effectiveAmountFe * 5L) >> 1;
            } else {
                mekanismAmount = clampToLong((double) effectiveAmountFe * feToJoules);
            }
            if (mekanismAmount <= 0L) {
                return 0L;
            }
            long remainder = target.insertEnergy(mekanismAmount, action);
            if (remainder <= 0L) {
                return effectiveAmountFe;
            }
            if (remainder >= mekanismAmount) {
                return 0L;
            }
            long acceptedJ = mekanismAmount - remainder;
            if (defaultConversion && acceptedJ <= J_FAST_PATH_LIMIT) {
                // 1 J = 2/5 FE → integer-only: shift + idiv (HotSpot lowers
                // /5L to a magic-number high-mul, so this is one imul + sar
                // on x64, no FPU.)
                return Math.min(effectiveAmountFe, (acceptedJ << 1) / 5L);
            }
            return Math.min(effectiveAmountFe, clampToLong((double) acceptedJ * joulesToFe));
        }
    }

    private record ForgeEnergyTarget(IEnergyStorage target) implements TargetAccess {
        @Nullable
        static TargetAccess resolve(EnergyCapCache cache, Direction side) {
            IEnergyStorage target = cache.getEnergyCap(Capabilities.EnergyStorage.BLOCK, side);
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
