package com.moakiee.ae2lt.logic;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;

import com.moakiee.ae2lt.logic.energy.AppFluxBridge;

/**
 * Helper facade for direct FE transfer call-sites.
 * Wireless energy routing lives in {@link AppFluxBridge}.
 */
public final class AppFluxHelper {

    @Nullable
    public static final AEKey FE_KEY = AppFluxBridge.FE_KEY;
    public static final long TRANSFER_RATE = AppFluxBridge.TRANSFER_RATE;

    private AppFluxHelper() {}

    public static boolean isAvailable() {
        return AppFluxBridge.isAvailable();
    }

    @Nullable
    public static Item getInductionCard() {
        return AppFluxBridge.getInductionCard();
    }

    public static boolean isInductionCard(Item item) {
        return AppFluxBridge.isInductionCard(item);
    }

    public static int simulateReceivable(EnergyHandler target) {
        if (!isAvailable()) {
            return 0;
        }
        try (var transaction = Transaction.openRoot()) {
            return target.insert(getTransferRateIntHint(), transaction);
        }
    }

    public static int pullPowerFromNetwork(MEStorage meStorage, EnergyHandler target, IActionSource source) {
        if (!isAvailable() || FE_KEY == null) {
            return 0;
        }

        int requested = simulateReceivable(target);
        if (requested <= 0) {
            return 0;
        }

        long extracted = meStorage.extract(FE_KEY, requested, Actionable.MODULATE, source);
        if (extracted <= 0L) {
            return 0;
        }

        int accepted;
        try (var transaction = Transaction.openRoot()) {
            accepted = target.insert((int) Math.min(extracted, Integer.MAX_VALUE), transaction);
            transaction.commit();
        }
        long remainder = extracted - accepted;
        if (remainder > 0L) {
            meStorage.insert(FE_KEY, remainder, Actionable.MODULATE, source);
        }
        return accepted;
    }

    private static int getTransferRateIntHint() {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, TRANSFER_RATE));
    }
}
