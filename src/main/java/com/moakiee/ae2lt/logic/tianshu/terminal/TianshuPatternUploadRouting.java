package com.moakiee.ae2lt.logic.tianshu.terminal;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.helpers.patternprovider.PatternContainer;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.item.ClosedLoopPatternItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Shared client/server routing policy for terminal pattern uploads. */
public final class TianshuPatternUploadRouting {
    private static final ResourceLocation MATTER_WARPING_MATRIX_PORT_ID =
            ResourceLocation.fromNamespaceAndPath(
                    AE2LightningTech.MODID, "matter_warping_matrix_port");

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
            case PROCESSING -> Route.PROCESSING_PROVIDER;
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

    /** The built-in matrix is the preferred destination for crafting-family patterns. */
    public static boolean isMatterWarpingMatrixTarget(PatternContainer target) {
        if (target == null) return false;
        var group = target.getTerminalGroup();
        return group != null && group.icon() != null
                && isMatterWarpingMatrixId(group.icon().getId());
    }

    static boolean isMatterWarpingMatrixId(ResourceLocation id) {
        return MATTER_WARPING_MATRIX_PORT_ID.equals(id);
    }

    /** Encoding is acknowledged only when the produced stack can be routed and decoded. */
    public static boolean isValidEncodingResult(ItemStack stack, Level level) {
        return classify(stack, level) != Route.INVALID;
    }
}
