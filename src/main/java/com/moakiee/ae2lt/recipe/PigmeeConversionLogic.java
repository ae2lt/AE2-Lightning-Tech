package com.moakiee.ae2lt.recipe;

import org.jetbrains.annotations.Nullable;

import com.moakiee.ae2lt.item.FixedInfiniteCellItem;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class PigmeeConversionLogic {
    private PigmeeConversionLogic() {
    }

    static boolean canConvert(ItemStack target) {
        return identify(target) != null;
    }

    static ItemStack createResult(ItemStack target) {
        Conversion conversion = identify(target);
        if (conversion == null) {
            return ItemStack.EMPTY;
        }

        return switch (conversion) {
            case HIGH_VOLTAGE -> FixedInfiniteCellItem.createDisplayedResultStack(
                    FixedInfiniteCellItem.CellOutcome.HIGH_VOLTAGE);
            case EXTREME_HIGH_VOLTAGE -> FixedInfiniteCellItem.createDisplayedResultStack(
                    FixedInfiniteCellItem.CellOutcome.EXTREME_HIGH_VOLTAGE);
            case LIGHTNING_COLLAPSE_MATRIX -> FixedInfiniteCellItem.createDisplayedResultStack(
                    FixedInfiniteCellItem.CellOutcome.LIGHTNING_COLLAPSE_MATRIX);
            case INFINITE_STORAGE -> new ItemStack(ModItems.INFINITE_STORAGE_CELL.get());
            case MULTIDIMENSIONAL_SUPERCOMPUTER ->
                    new ItemStack(ModBlocks.MULTIDIMENSIONAL_SUPERCOMPUTING_UNIT.asItem());
            case MULTIDIMENSIONAL_MATRIX ->
                    new ItemStack(ModBlocks.MATTER_WARPING_MATRIX_MULTIDIMENSIONAL_MAIN_CORE.asItem());
        };
    }

    @Nullable
    private static Conversion identify(ItemStack target) {
        if (target.is(Items.LIGHTNING_ROD)) {
            return Conversion.HIGH_VOLTAGE;
        }
        if (target.is(ModItems.THUNDERSTORM_CONDENSATE.get())) {
            return Conversion.EXTREME_HIGH_VOLTAGE;
        }
        if (target.is(ModItems.LIGHTNING_COLLAPSE_MATRIX.get())) {
            return Conversion.LIGHTNING_COLLAPSE_MATRIX;
        }
        if (target.is(ModItems.BULK_LIGHTNING_STORAGE_COMPONENT.get())) {
            return Conversion.INFINITE_STORAGE;
        }
        if (target.is(ModBlocks.OVERLOAD_SUPERCOMPUTING_UNIT.asItem())) {
            return Conversion.MULTIDIMENSIONAL_SUPERCOMPUTER;
        }
        if (target.is(ModBlocks.MATTER_WARPING_MATRIX_OVERLOAD_MAIN_CORE.asItem())) {
            return Conversion.MULTIDIMENSIONAL_MATRIX;
        }
        return null;
    }

    private enum Conversion {
        HIGH_VOLTAGE,
        EXTREME_HIGH_VOLTAGE,
        LIGHTNING_COLLAPSE_MATRIX,
        INFINITE_STORAGE,
        MULTIDIMENSIONAL_SUPERCOMPUTER,
        MULTIDIMENSIONAL_MATRIX
    }
}
