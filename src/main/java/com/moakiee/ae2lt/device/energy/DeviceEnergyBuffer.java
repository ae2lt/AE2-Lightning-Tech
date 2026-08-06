package com.moakiee.ae2lt.device.energy;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.energy.IEnergyStorage;

import appeng.api.stacks.AEKey;

/**
 * Unified energy buffer interface for overload devices.
 */
public interface DeviceEnergyBuffer {

    long stored(ItemStack stack);

    long capacity(ItemStack stack);

    boolean tryConsume(ItemStack stack, ServerPlayer player, long amount);

    void refill(ItemStack stack, ServerPlayer player);

    default int receiveFe(ItemStack stack, int amount, boolean simulate) {
        return 0;
    }

    default IEnergyStorage asEnergyStorage(ItemStack stack) {
        return null;
    }

    /** Optional: typed extraction (HV/EHV ammo, fluids, ...). Default: unsupported. */
    default boolean tryConsumeKey(ItemStack stack, ServerPlayer player, AEKey key, long amount) {
        return false;
    }
}
