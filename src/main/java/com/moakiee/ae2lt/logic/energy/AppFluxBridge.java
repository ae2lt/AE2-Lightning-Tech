package com.moakiee.ae2lt.logic.energy;

import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.Capabilities;

import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;

/**
 * Bridge for Applied Flux. When AppFlux is present at runtime, delegates to
 * {@link AppFluxAccess} for direct (zero-reflection) API calls. When absent,
 * all methods return safe defaults (null / 0 / false).
 *
 * <p>This class intentionally contains NO AppFlux imports so it can always be
 * loaded by the JVM regardless of whether AppFlux is installed.
 */
public final class AppFluxBridge {

    private static final ResourceLocation INDUCTION_CARD_ID =
            ResourceLocation.fromNamespaceAndPath("appflux", "induction_card");

    private static final boolean LOADED;

    @Nullable
    public static final AEKey FE_KEY;
    public static final long TRANSFER_RATE;

    static {
        boolean available;
        try {
            Class.forName("com.glodblock.github.appflux.api.IFluxCell");
            available = true;
        } catch (ClassNotFoundException e) {
            available = false;
        }
        LOADED = available;
        FE_KEY = LOADED ? AppFluxAccess.FE_KEY : null;
        TRANSFER_RATE = LOADED ? AppFluxAccess.TRANSFER_RATE : 0L;
    }

    private AppFluxBridge() {}

    public static boolean isAvailable() {
        return FE_KEY != null && TRANSFER_RATE > 0;
    }

    public static boolean canUseEnergyHandler() {
        return LOADED && isAvailable();
    }

    @Nullable
    public static Item getInductionCard() {
        Item card = BuiltInRegistries.ITEM.get(INDUCTION_CARD_ID);
        return card != null && card != Items.AIR ? card : null;
    }

    public static boolean isInductionCard(Item item) {
        Item card = getInductionCard();
        return card != null && card == item;
    }

    public static boolean isFluxCell(ItemStack stack) {
        return LOADED && AppFluxAccess.isFluxCell(stack);
    }

    public static long getFluxCellCapacity(ItemStack stack) {
        return LOADED ? AppFluxAccess.getFluxCellCapacity(stack) : 0L;
    }

    public static void persistCellStorage(@Nullable MEStorage storage) {
        if (LOADED) {
            AppFluxAccess.persistCellStorage(storage);
        }
    }

    @Nullable
    public static Object createCapCache(ServerLevel level, BlockPos pos,
                                        Supplier<IGrid> gridSupplier) {
        return LOADED ? AppFluxAccess.createCapCache(level, pos, gridSupplier) : null;
    }

    @Nullable
    public static TargetAccess resolveEnergyTarget(@Nullable Object energyCapCache, Direction side) {
        return LOADED ? AppFluxAccess.resolveEnergyTarget(energyCapCache, side) : null;
    }

    public static long simulateTarget(@Nullable TargetAccess target, long maxFe) {
        return LOADED ? AppFluxAccess.simulateTarget(target, maxFe) : 0L;
    }

    public static long sendToTarget(@Nullable TargetAccess target, IStorageService storage,
                                    IActionSource source, long maxFe) {
        return LOADED ? AppFluxAccess.sendToTarget(target, storage, source, maxFe) : 0L;
    }

    public static long sendToTargetKnownDemand(@Nullable TargetAccess target, IStorageService storage,
                                               IActionSource source, long requested) {
        return LOADED ? AppFluxAccess.sendToTargetKnownDemand(target, storage, source, requested) : 0L;
    }

    public static long sendToTargetKnownDemand(@Nullable TargetAccess target, BufferedMEStorage buffer,
                                               IActionSource source, long requested) {
        return LOADED ? AppFluxAccess.sendToTargetKnownDemand(target, buffer, source, requested) : 0L;
    }

    public static long sendToTargetRepeatedOptimistic(@Nullable TargetAccess target, BufferedMEStorage buffer,
                                                      IActionSource source, long maxFe, int maxCalls) {
        return LOADED ? AppFluxAccess.sendToTargetRepeatedOptimistic(target, buffer, source, maxFe, maxCalls) : 0L;
    }

    public static boolean hasEnergyCapability(ServerLevel level, BlockPos pos,
                                              Direction face) {
        return level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, face) != null;
    }
}
