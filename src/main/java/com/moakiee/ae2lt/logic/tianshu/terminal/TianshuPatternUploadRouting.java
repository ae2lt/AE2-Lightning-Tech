package com.moakiee.ae2lt.logic.tianshu.terminal;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.crafting.pattern.AECraftingPattern;
import appeng.crafting.pattern.AESmithingTablePattern;
import appeng.crafting.pattern.AEStonecuttingPattern;
import com.moakiee.ae2lt.item.ClosedLoopPatternItem;
import java.util.Set;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Shared client/server routing policy for terminal pattern uploads. */
public final class TianshuPatternUploadRouting {
    private static final Set<String> CRAFTING_ASSEMBLER_IDS = Set.of(
            "extendedae_plus:assembler_matrix_pattern_plus",
            "extendedae:assembler_matrix_pattern",
            "ae2:molecular_assembler",
            "extendedae:ex_molecular_assembler");

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
        if (stack.getItem() instanceof ClosedLoopPatternItem) return Route.CLOSED_LOOP_STORAGE;
        var details = PatternDetailsHelper.decodePattern(stack, level);
        if (details == null) return Route.INVALID;
        if (details instanceof AECraftingPattern
                || details instanceof AESmithingTablePattern
                || details instanceof AEStonecuttingPattern) {
            return Route.CRAFTING_ASSEMBLER;
        }
        return Route.PROCESSING_PROVIDER;
    }

    /** Accepts AE2 and addon assembly matrices without taking a hard dependency on those addons. */
    public static boolean isCraftingAssemblerGroup(PatternContainerGroup group) {
        if (group == null || group.icon() == null) return false;
        return isCraftingAssemblerId(group.icon().getId().toString());
    }

    public static boolean isCraftingAssemblerId(String id) {
        return id != null && CRAFTING_ASSEMBLER_IDS.contains(id);
    }
}
