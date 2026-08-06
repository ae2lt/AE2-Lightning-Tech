package com.moakiee.ae2lt.device.energy;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.energy.IEnergyStorage;

import com.moakiee.ae2lt.logic.railgun.RailgunEnergyBuffer;

/**
 * {@link DeviceEnergyBuffer} backed by the railgun's FE buffer and optional
 * AppFlux network shortfall pulls.
 */
public final class NetworkBoundEnergyBuffer implements DeviceEnergyBuffer {

    public static final NetworkBoundEnergyBuffer INSTANCE = new NetworkBoundEnergyBuffer();

    private NetworkBoundEnergyBuffer() {}

    @Override
    public long stored(ItemStack stack) {
        return RailgunEnergyBuffer.read(stack);
    }

    @Override
    public long capacity(ItemStack stack) {
        return RailgunEnergyBuffer.capacity(stack);
    }

    @Override
    public boolean tryConsume(ItemStack stack, ServerPlayer player, long amount) {
        return RailgunEnergyBuffer.tryConsume(stack, player, amount);
    }

    @Override
    public void refill(ItemStack stack, ServerPlayer player) {
        RailgunEnergyBuffer.refillFromNetwork(
                stack,
                player,
                Math.max(0L, RailgunEnergyBuffer.capacity(stack) - RailgunEnergyBuffer.read(stack)));
    }

    @Override
    public int receiveFe(ItemStack stack, int amount, boolean simulate) {
        return RailgunEnergyBuffer.receiveFe(stack, amount, simulate);
    }

    @Override
    public IEnergyStorage asEnergyStorage(ItemStack stack) {
        return RailgunEnergyBuffer.asEnergyStorage(stack);
    }
}
