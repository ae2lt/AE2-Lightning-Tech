package com.moakiee.ae2lt.logic.tianshu.terminal;

import appeng.api.crafting.PatternDetailsHelper;
import com.moakiee.ae2lt.item.ClosedLoopPatternItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Shared client/server routing policy for terminal pattern uploads. */
public final class TianshuPatternUploadRouting {
    public enum Route {
        CLOSED_LOOP_STORAGE,
        CRAFTING_ASSEMBLER,
        PROCESSING_PROVIDER,
        INVALID
    }

    private TianshuPatternUploadRouting() {
    }

    /** Mirrors EAEP's scheduler: only processing-family modes open the provider picker. */
    public static Route forEncodingMode(TianshuEncodingMode mode) {
        if (mode == null) return Route.INVALID;
        return switch (mode) {
            case CLOSED_LOOP -> Route.CLOSED_LOOP_STORAGE;
            case CRAFTING, STONECUTTING, SMITHING_TABLE -> Route.CRAFTING_ASSEMBLER;
            case PROCESSING, ADVANCED, OVERLOAD -> Route.PROCESSING_PROVIDER;
        };
    }

    public static Route classify(ItemStack stack, Level level) {
        if (stack == null || stack.isEmpty() || level == null) return Route.INVALID;
        if (stack.getItem() instanceof ClosedLoopPatternItem item) {
            return item.readPayload(stack, level).isPresent()
                    ? Route.CLOSED_LOOP_STORAGE : Route.INVALID;
        }
        var details = PatternDetailsHelper.decodePattern(stack, level);
        if (details == null) return Route.INVALID;
        if (!details.supportsPushInputsToExternalInventory()) {
            return Route.CRAFTING_ASSEMBLER;
        }
        return Route.PROCESSING_PROVIDER;
    }

    /** Encoding is acknowledged only when the produced stack can be routed and decoded. */
    public static boolean isValidEncodingResult(ItemStack stack, Level level) {
        return classify(stack, level) != Route.INVALID;
    }
}
