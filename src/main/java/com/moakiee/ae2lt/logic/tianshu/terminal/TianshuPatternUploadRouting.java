package com.moakiee.ae2lt.logic.tianshu.terminal;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.item.ClosedLoopPatternItem;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Shared client/server routing policy for terminal pattern uploads. */
public final class TianshuPatternUploadRouting {
    private static final ResourceLocation MATTER_WARPING_MATRIX_PORT_ID =
            ResourceLocation.fromNamespaceAndPath(
                    AE2LightningTech.MODID, "matter_warping_matrix_port");
    private static final Set<ResourceLocation> CRAFTING_UPLOAD_GROUP_IDS = Set.of(
            ResourceLocation.fromNamespaceAndPath("ae2", "molecular_assembler"),
            ResourceLocation.fromNamespaceAndPath("extendedae", "ex_molecular_assembler"),
            ResourceLocation.fromNamespaceAndPath("extendedae", "assembler_matrix_pattern"),
            ResourceLocation.fromNamespaceAndPath(
                    "extendedae_plus", "assembler_matrix_pattern_plus"),
            ResourceLocation.fromNamespaceAndPath("neoecoae", "crafting_system_l4"),
            ResourceLocation.fromNamespaceAndPath("neoecoae", "crafting_system_l6"),
            ResourceLocation.fromNamespaceAndPath("neoecoae", "crafting_system_l9"),
            MATTER_WARPING_MATRIX_PORT_ID);

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

    /** Exact group whitelist for automatic crafting-family uploads. */
    public static boolean isCraftingUploadGroup(PatternContainerGroup group) {
        return group != null && group.icon() != null
                && isCraftingUploadGroupId(group.icon().getId());
    }

    static boolean isCraftingUploadGroupId(ResourceLocation id) {
        return id != null && CRAFTING_UPLOAD_GROUP_IDS.contains(id);
    }

    /** The built-in matrix is tried before every other whitelisted crafting group. */
    public static boolean isMatterWarpingMatrixGroup(PatternContainerGroup group) {
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
