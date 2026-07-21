package com.moakiee.ae2lt.registry;

import com.moakiee.ae2lt.AE2LightningTech;
import net.minecraft.resources.ResourceLocation;

/**
 * Registry aliases for IDs used before the Tianshu multiblock naming was finalized.
 *
 * <p>Aliases migrate world palettes and item stacks without keeping duplicate
 * compatibility blocks registered. IDs that already match the final names remain unchanged.</p>
 */
public final class LegacyRegistryAliases {
    private static final String MATRIX_PREFIX = "matter_warping_matrix_";
    private static boolean registered;

    private LegacyRegistryAliases() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        aliasTianshuComputeUnits();

        aliasMatrixBlock("blank_sub_core", "blank_unit");
        aliasMatrixBlock("thread_sub_core_t1", "thread_unit_t1");
        aliasMatrixBlock("thread_sub_core_t2", "thread_unit_t2");
        aliasMatrixBlock("cooling_sub_core_t1", "thermal_control_unit_t1");
        aliasMatrixBlock("cooling_sub_core_t2", "thermal_control_unit_t2");
        aliasBlockAndItem(MATRIX_PREFIX + "multiplier_sub_core_t1", "tianshu_amplifier_unit");
        aliasBlockAndItem(MATRIX_PREFIX + "multiplier_sub_core_t2", "tianshu_amplifier_unit");
    }

    private static void aliasTianshuComputeUnits() {
        aliasBlockAndItem("baseline_supercomputing_unit", "tianshu_baseline_main_core");
        aliasBlockAndItem("quantum_supercomputing_unit", "tianshu_quantum_main_core");
        aliasBlockAndItem("overload_supercomputing_unit", "tianshu_overload_main_core");
        aliasBlockAndItem("multidimensional_supercomputing_unit", "tianshu_multidimensional_main_core");
        aliasBlockAndItem("amplifier_supercomputing_unit", "tianshu_amplifier_unit");
    }

    private static void aliasMatrixBlock(String oldSuffix, String newSuffix) {
        String target = MATRIX_PREFIX + newSuffix;
        aliasBlockAndItem(MATRIX_PREFIX + oldSuffix, target);
    }

    private static void aliasBlockAndItem(String from, String to) {
        ModBlocks.BLOCKS.addAlias(id(from), id(to));
        ModItems.ITEMS.addAlias(id(from), id(to));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, path);
    }
}
