package com.moakiee.ae2lt.celestweave;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.energy.IEnergyStorage;

import com.moakiee.ae2lt.device.energy.DeviceEnergyBuffer;

public final class ArmorDeviceEnergyBuffer implements DeviceEnergyBuffer {
    public static final ArmorDeviceEnergyBuffer INSTANCE = new ArmorDeviceEnergyBuffer();

    private ArmorDeviceEnergyBuffer() {
    }

    @Override
    public long stored(ItemStack stack) {
        return ArmorEnergyBuffer.read(stack);
    }

    @Override
    public long capacity(ItemStack stack) {
        return ArmorEnergyBuffer.capacity(stack);
    }

    @Override
    public boolean tryConsume(ItemStack stack, ServerPlayer player, long amount) {
        return ArmorEnergyBuffer.tryConsume(stack, player, amount);
    }

    @Override
    public void refill(ItemStack stack, ServerPlayer player) {
        ArmorEnergyBuffer.refillFromNetwork(
                stack,
                player,
                Math.max(0L,
                        ArmorEnergyBuffer.capacity(stack, player.registryAccess())
                                - ArmorEnergyBuffer.read(stack, player.registryAccess())));
    }

    @Override
    public int receiveFe(ItemStack stack, int amount, boolean simulate) {
        return ArmorEnergyBuffer.receiveFe(stack, amount, simulate);
    }

    @Override
    public IEnergyStorage asEnergyStorage(ItemStack stack) {
        return ArmorEnergyBuffer.asEnergyStorage(stack);
    }
}
