package com.moakiee.ae2lt.logic.advancement;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.item.FixedInfiniteCellItem;
import com.moakiee.ae2lt.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Awards progression criteria whose conditions cannot be represented accurately
 * by vanilla item predicates.
 */
public final class ProgressionAdvancementService {
    private static final ResourceLocation ANNIHILATION =
            advancement("annihilation");
    private static final ResourceLocation NEUTRALIZATION =
            advancement("neutralization");
    private static final ResourceLocation CONSTRUCT_FRAGMENT =
            advancement("construct_fragment");
    private static final ResourceLocation THUNDERSTORM_GENERATOR =
            advancement("thunderstorm_generator");
    private static final ResourceLocation OBSERVABLE_BLACK_HOLE =
            advancement("observable_black_hole");

    private ProgressionAdvancementService() {
    }

    public static void awardOverloadTntIgnited(ServerPlayer player) {
        award(player, ANNIHILATION, "ignite_overload_tnt");
    }

    public static void inspectMysteriousCell(ServerPlayer player, ItemStack stack) {
        if (!stack.is(ModItems.MYSTERIOUS_CELL.get())) {
            return;
        }

        if (!FixedInfiniteCellItem.hasType(stack)) {
            award(player, NEUTRALIZATION, "obtain_mysterious_component");
            return;
        }

        switch (FixedInfiniteCellItem.CellOutcome.fromTypeId(FixedInfiniteCellItem.getType(stack))) {
            case LIGHTNING_ROD ->
                    award(player, CONSTRUCT_FRAGMENT, "obtain_infinite_lightning_rod");
            case HIGH_VOLTAGE ->
                    award(player, THUNDERSTORM_GENERATOR, "obtain_infinite_high_voltage");
            case EXTREME_HIGH_VOLTAGE ->
                    award(player, THUNDERSTORM_GENERATOR, "obtain_infinite_extreme_high_voltage");
            case LIGHTNING_COLLAPSE_MATRIX ->
                    award(player, OBSERVABLE_BLACK_HOLE, "obtain_infinite_collapse_matrix");
            case RESEARCH_NOTE, MOAKIEE_FUMO, CYSTRYSU_FUMO -> {
                // These outcomes have distinct real output items and use vanilla
                // inventory_changed criteria after being extracted.
            }
        }
    }

    private static ResourceLocation advancement(String path) {
        return ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "main/" + path);
    }

    private static void award(ServerPlayer player, ResourceLocation id, String criterion) {
        var advancement = player.server.getAdvancements().getAdvancement(id);
        if (advancement != null) {
            player.getAdvancements().award(advancement, criterion);
        }
    }
}
