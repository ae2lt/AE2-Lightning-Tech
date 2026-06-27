package com.moakiee.ae2lt.celestweave;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.energy.IEnergyStorage;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;

import com.moakiee.ae2lt.device.network.ArmorNetworkBinding;
import com.moakiee.ae2lt.logic.energy.AppFluxBridge;
import com.moakiee.ae2lt.registry.ModDataComponents;

public final class ArmorEnergyBuffer {
    private ArmorEnergyBuffer() {
    }

    public static long read(ItemStack stack) {
        return read(stack, null);
    }

    public static long read(ItemStack stack, HolderLookup.Provider registries) {
        Long value = stack.get(ModDataComponents.CELESTWEAVE_ENERGY_BUFFER.get());
        return Math.max(0L, Math.min(capacity(stack, registries), value == null ? 0L : value));
    }

    public static long capacity(ItemStack stack) {
        return capacity(stack, null);
    }

    public static long capacity(ItemStack stack, HolderLookup.Provider registries) {
        return ArmorEnergyRules.capacityForExtraModuleFe(ArmorEnergyModuleStorage.capacityFe(stack, registries));
    }

    public static void write(ItemStack stack, long value) {
        write(stack, null, value);
    }

    public static void write(ItemStack stack, HolderLookup.Provider registries, long value) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        stack.set(ModDataComponents.CELESTWEAVE_ENERGY_BUFFER.get(), Math.max(0L, Math.min(capacity(stack, registries), value)));
    }

    public static void clamp(ItemStack stack) {
        write(stack, read(stack));
    }

    public static void clamp(ItemStack stack, HolderLookup.Provider registries) {
        write(stack, registries, read(stack, registries));
    }

    public static boolean tryConsume(ItemStack stack, ServerPlayer player, long amount) {
        if (amount <= 0L) {
            return true;
        }
        var registries = player.registryAccess();
        long buffered = read(stack, registries);
        if (buffered >= amount) {
            write(stack, registries, buffered - amount);
            return true;
        }
        return false;
    }

    public static long refillFromNetwork(ItemStack stack, ServerPlayer player, long maxAmount) {
        if (stack == null || stack.isEmpty() || player == null || maxAmount <= 0L) {
            return 0L;
        }
        var registries = player.registryAccess();
        long room = capacity(stack, registries) - read(stack, registries);
        if (room <= 0L) {
            return 0L;
        }
        if (!AppFluxBridge.isAvailable() || AppFluxBridge.FE_KEY == null) {
            return 0L;
        }

        var bound = ArmorNetworkBinding.INSTANCE.resolve(stack, player);
        if (!bound.success()) {
            return 0L;
        }
        IGrid grid = bound.grid();
        if (grid == null) {
            return 0L;
        }
        var storage = grid.getStorageService().getInventory();
        IActionSource source = IActionSource.ofPlayer(player);
        long request = Math.min(room, maxAmount);
        long got = storage.extract(AppFluxBridge.FE_KEY, request, Actionable.MODULATE, source);
        if (got <= 0L) {
            return 0L;
        }
        write(stack, registries, read(stack, registries) + got);
        return got;
    }

    public static int receiveFe(ItemStack stack, int amount, boolean simulate) {
        return receiveFe(stack, null, amount, simulate);
    }

    public static int receiveFe(ItemStack stack, HolderLookup.Provider registries, int amount, boolean simulate) {
        int accepted = ArmorEnergyRules.receivableFe(read(stack, registries), capacity(stack, registries), amount);
        if (!simulate && accepted > 0) {
            write(stack, registries, read(stack, registries) + accepted);
        }
        return accepted;
    }

    public static IEnergyStorage asEnergyStorage(ItemStack stack) {
        return new IEnergyStorage() {
            @Override
            public int receiveEnergy(int maxReceive, boolean simulate) {
                return receiveFe(stack, maxReceive, simulate);
            }

            @Override
            public int extractEnergy(int maxExtract, boolean simulate) {
                return 0;
            }

            @Override
            public int getEnergyStored() {
                return (int) Math.min(Integer.MAX_VALUE, read(stack));
            }

            @Override
            public int getMaxEnergyStored() {
                return (int) Math.min(Integer.MAX_VALUE, capacity(stack));
            }

            @Override
            public boolean canExtract() {
                return false;
            }

            @Override
            public boolean canReceive() {
                return true;
            }
        };
    }
}
