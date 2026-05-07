package com.moakiee.ae2lt.machine.common;

import java.util.Optional;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;

import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.me.key.LightningKey;

public final class SingleOutputLightningRecipeExecutor {
    private SingleOutputLightningRecipeExecutor() {
    }

    public static boolean complete(
            int inputStartSlot,
            int inputEndSlot,
            IntUnaryOperator slotConsumption,
            ItemStack result,
            Supplier<Optional<LightningPlan>> lightningPlanSupplier,
            InventoryAdapter inventory,
            LightningAdapter lightning) {
        if (!inventory.canAcceptOutput(result)) {
            return false;
        }

        for (int slot = inputStartSlot; slot <= inputEndSlot; slot++) {
            int toConsume = slotConsumption.applyAsInt(slot);
            if (toConsume <= 0) {
                continue;
            }
            if (inventory.getStackInSlot(slot).getCount() < toConsume) {
                return false;
            }
        }

        Optional<LightningPlan> lightningPlan = lightningPlanSupplier.get();
        if (lightningPlan.isEmpty()) {
            return false;
        }

        LightningPlan plan = lightningPlan.get();
        if (lightning.simulateExtract(plan.key(), plan.amount()) < plan.amount()) {
            return false;
        }

        ItemStack[] extractedInputs = new ItemStack[inputEndSlot + 1];
        for (int slot = inputStartSlot; slot <= inputEndSlot; slot++) {
            int toConsume = slotConsumption.applyAsInt(slot);
            if (toConsume <= 0) {
                continue;
            }

            ItemStack extracted = inventory.extractItem(slot, toConsume);
            if (extracted.getCount() != toConsume) {
                rollbackInputs(inputStartSlot, inputEndSlot, extractedInputs, inventory);
                return false;
            }
            extractedInputs[slot] = extracted;
        }

        long extractedLightning = lightning.extract(plan.key(), plan.amount());
        if (extractedLightning < plan.amount()) {
            rollbackInputs(inputStartSlot, inputEndSlot, extractedInputs, inventory);
            if (extractedLightning > 0L) {
                lightning.insert(plan.key(), extractedLightning);
            }
            return false;
        }

        if (!inventory.insertOutput(result).isEmpty()) {
            lightning.insert(plan.key(), extractedLightning);
            rollbackInputs(inputStartSlot, inputEndSlot, extractedInputs, inventory);
            return false;
        }

        return true;
    }

    private static void rollbackInputs(
            int inputStartSlot,
            int inputEndSlot,
            ItemStack[] extractedInputs,
            InventoryAdapter inventory) {
        for (int slot = inputStartSlot; slot <= inputEndSlot; slot++) {
            ItemStack extracted = extractedInputs[slot];
            if (extracted != null && !extracted.isEmpty()) {
                inventory.insertInput(slot, extracted);
            }
        }
    }

    public record LightningPlan(LightningKey key, long amount) {
    }

    public interface InventoryAdapter {
        boolean canAcceptOutput(ItemStack result);

        ItemStack getStackInSlot(int slot);

        ItemStack extractItem(int slot, int amount);

        ItemStack insertOutput(ItemStack stack);

        void insertInput(int slot, ItemStack stack);
    }

    public interface LightningAdapter {
        long simulateExtract(LightningKey key, long amount);

        long extract(LightningKey key, long amount);

        long insert(LightningKey key, long amount);
    }
}
