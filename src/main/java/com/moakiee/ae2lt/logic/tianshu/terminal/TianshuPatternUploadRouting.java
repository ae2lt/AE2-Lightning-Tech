package com.moakiee.ae2lt.logic.tianshu.terminal;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.crafting.pattern.AECraftingPattern;
import appeng.crafting.pattern.AESmithingTablePattern;
import appeng.crafting.pattern.AEStonecuttingPattern;
import com.moakiee.ae2lt.item.ClosedLoopPatternItem;
import java.util.Locale;
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
        return isCraftingAssemblerPath(group.icon().getId().getPath());
    }

    public static boolean isCraftingAssemblerPath(String rawPath) {
        if (rawPath == null) return false;
        String path = rawPath.toLowerCase(Locale.ROOT);
        return path.contains("molecular_assembler") || path.contains("assembler_matrix");
    }
}
