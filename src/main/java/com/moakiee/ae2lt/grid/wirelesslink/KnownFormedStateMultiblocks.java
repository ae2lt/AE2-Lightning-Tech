package com.moakiee.ae2lt.grid.wirelesslink;

final class KnownFormedStateMultiblocks {
    private static final String ADVANCED_AE_CRAFTING_BLOCK_ENTITY =
            "net.pedroksl.advanced_ae.common.entities.AdvCraftingBlockEntity";
    private static final String EXTENDED_AE_ASSEMBLER_MATRIX_PREFIX =
            "com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrix";

    private KnownFormedStateMultiblocks() {
    }

    static boolean matches(String ownerClassName, String blockNamespace, String blockPath) {
        return ADVANCED_AE_CRAFTING_BLOCK_ENTITY.equals(ownerClassName)
                || ownerClassName.startsWith(EXTENDED_AE_ASSEMBLER_MATRIX_PREFIX)
                || ("advanced_ae".equals(blockNamespace) && blockPath.startsWith("quantum_"))
                || ("extendedae".equals(blockNamespace) && blockPath.startsWith("assembler_matrix_"));
    }
}
