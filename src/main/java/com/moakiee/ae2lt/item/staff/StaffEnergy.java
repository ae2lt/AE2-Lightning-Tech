package com.moakiee.ae2lt.item.staff;

import com.moakiee.ae2lt.registry.ModDataComponents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** One persisted FE buffer; native durability callbacks pay uses, execution pays its surcharge. */
public final class StaffEnergy {
    public static final int CAPACITY = 100_000_000;
    public static final int FE_PER_USE = 1_000;

    private StaffEnergy() {}

    public static int stored(ItemStack stack) {
        if (StaffPhaseService.isProjection(stack) && StaffPhaseService.clientView()) return StaffPhaseService.view(stack).energy();
        stack = StaffPhaseService.resolve(stack);
        return Math.clamp(stack.getOrDefault(ModDataComponents.MIMICRY_ENERGY.get(), 0), 0, CAPACITY);
    }

    public static boolean canUse(ItemStack stack) { return canPay(stack, 1); }

    public static boolean canPay(ItemStack stack, int units) {
        return stored(stack) >= Math.max(0L, (long) units) * FE_PER_USE;
    }

    public static void consume(ItemStack stack, int units) {
        stack = StaffPhaseService.resolve(stack);
        if (stack.isEmpty()) return;
        if (units > 0) stack.set(ModDataComponents.MIMICRY_ENERGY.get(),
                (int) Math.max(0L, stored(stack) - (long) units * FE_PER_USE));
    }

    public static boolean tryConsumeExecution(ItemStack stack, long amount) {
        stack = StaffPhaseService.resolve(stack);
        if (stack.isEmpty()) return false;
        // Reserve the native successful-hit payment that follows the execution callback.
        if (amount < 0 || amount > (long) stored(stack) - FE_PER_USE) return false;
        stack.set(ModDataComponents.MIMICRY_ENERGY.get(), stored(stack) - (int) amount);
        return true;
    }

    public static ItemStack charged(ItemStack stack) {
        if (StaffPhaseService.isProjection(stack)) return stack;
        stack.set(ModDataComponents.MIMICRY_ENERGY.get(), CAPACITY);
        return stack;
    }

    public static IEnergyStorage capability(ItemStack stack) {
        return new IEnergyStorage() {
            @Override public int receiveEnergy(int requested, boolean simulate) {
                var original = StaffPhaseService.resolve(stack);
                if (original.isEmpty()) return 0;
                int accepted = Math.min(Math.max(0, requested), CAPACITY - stored(stack));
                if (!simulate && accepted > 0) original.set(ModDataComponents.MIMICRY_ENERGY.get(), stored(original) + accepted);
                return accepted;
            }
            @Override public int extractEnergy(int amount, boolean simulate) { return 0; }
            @Override public int getEnergyStored() { return stored(stack); }
            @Override public int getMaxEnergyStored() { return CAPACITY; }
            @Override public boolean canExtract() { return false; }
            @Override public boolean canReceive() { return true; }
        };
    }
}
