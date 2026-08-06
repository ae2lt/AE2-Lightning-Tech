package com.moakiee.ae2lt.logic;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.energy.IEnergyStorage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;

import com.moakiee.ae2lt.logic.energy.AppFluxBridge;

/**
 * Backwards-compatible helper facade for existing direct FE transfer call-sites.
 * Newer wireless energy routing lives in {@link AppFluxBridge}.
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

    public static int simulateReceivable(IEnergyStorage target) {
        if (!isAvailable()) {
            return 0;
        }
        return target.receiveEnergy(getTransferRateIntHint(), true);
    }

    public static int pullPowerFromNetwork(MEStorage meStorage, IEnergyStorage target, IActionSource source) {
        if (!isAvailable() || FE_KEY == null) {
            return 0;
        }

        int requested = target.receiveEnergy(getTransferRateIntHint(), true);
        if (requested <= 0) {
            return 0;
        }

        long extracted = meStorage.extract(FE_KEY, requested, Actionable.MODULATE, source);
        if (extracted <= 0L) {
            return 0;
        }

        int accepted = target.receiveEnergy((int) Math.min(extracted, Integer.MAX_VALUE), false);
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
