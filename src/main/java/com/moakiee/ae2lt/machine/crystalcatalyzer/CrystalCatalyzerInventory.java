package com.moakiee.ae2lt.machine.crystalcatalyzer;

import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalCatalyzerRecipeService;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.Mode;
import com.moakiee.ae2lt.machine.lightningchamber.LargeStackItemHandler;
import com.moakiee.ae2lt.registry.ModItems;

/**
 * Crystal Catalyzer machine inventory.
 *
 * <p>Slot layout:
 * 0 = catalyst (256) —— parallel = amount / catalystCount
 * 1 = lightning collapse matrix (1)
 * 2 = output (1024, machine-write only)</p>
 */
public class CrystalCatalyzerInventory extends LargeStackItemHandler {
    public static final int SLOT_CATALYST = 0;
    public static final int SLOT_MATRIX = 1;
    public static final int SLOT_OUTPUT = 2;
    public static final int SLOT_COUNT = 3;

    public static final int CATALYST_SLOT_LIMIT = 256;
    public static final int OUTPUT_SLOT_LIMIT = 1024;
    public static final int MATRIX_SLOT_LIMIT = 1;

    @Nullable
    private Level level;

    private final Supplier<Mode> modeSupplier;

    public CrystalCatalyzerInventory(@Nullable Runnable changeListener) {
        this(changeListener, () -> Mode.CRYSTAL);
    }

    public CrystalCatalyzerInventory(@Nullable Runnable changeListener, Supplier<Mode> modeSupplier) {
        super(SLOT_COUNT, changeListener);
        this.modeSupplier = modeSupplier != null ? modeSupplier : () -> Mode.CRYSTAL;
    }

    public void setLevel(@Nullable Level level) {
        this.level = level;
    }

    @Override
    public int getSlotLimit(int slot) {
        validateSlotIndex(slot);
        return switch (slot) {
            case SLOT_CATALYST -> CATALYST_SLOT_LIMIT;
            case SLOT_MATRIX -> MATRIX_SLOT_LIMIT;
            case SLOT_OUTPUT -> OUTPUT_SLOT_LIMIT;
            default -> OUTPUT_SLOT_LIMIT;
        };
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        validateSlotIndex(slot);
        if (stack.isEmpty()) {
            return false;
        }

        return switch (slot) {
            case SLOT_MATRIX -> isLightningCollapseMatrix(stack);
            case SLOT_CATALYST -> CrystalCatalyzerRecipeService.isKnownCatalyst(level, stack, modeSupplier.get());
            case SLOT_OUTPUT -> false;
            default -> false;
        };
    }

    public boolean isLightningCollapseMatrix(ItemStack stack) {
        return stack.is(ModItems.LIGHTNING_COLLAPSE_MATRIX.get());
    }

    public boolean hasLightningCollapseMatrix() {
        return isLightningCollapseMatrix(getStackInSlot(SLOT_MATRIX));
    }

    public ItemStack insertRecipeOutput(ItemStack stack, boolean simulate) {
        return insertItemUnchecked(SLOT_OUTPUT, stack, simulate);
    }

    public boolean canAcceptRecipeOutput(ItemStack stack) {
        return insertRecipeOutput(stack, true).isEmpty();
    }

    public void setClientRenderStack(int slot, ItemStack stack) {
        setStackInSlotUnchecked(slot, stack);
    }
}
