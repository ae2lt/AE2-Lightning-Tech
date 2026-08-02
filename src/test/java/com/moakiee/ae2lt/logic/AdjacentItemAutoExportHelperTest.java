package com.moakiee.ae2lt.logic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.LoadingModList;

import com.moakiee.ae2lt.machine.overloadfactory.OverloadProcessingFactoryInventory;

class AdjacentItemAutoExportHelperTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void outputProbeHonorsTheAutoExportGate() {
        var inventory = new OverloadProcessingFactoryInventory(null);

        assertFalse(hasItemOutput(inventory, true));
        assertTrue(inventory.insertRecipeOutputs(List.of(new ItemStack(Items.GOLD_INGOT))));
        assertTrue(hasItemOutput(inventory, true));
        assertFalse(hasItemOutput(inventory, false));
    }

    private static boolean hasItemOutput(
            OverloadProcessingFactoryInventory inventory,
            boolean autoExport) {
        return AdjacentItemAutoExportHelper.hasAnyOutput(
                autoExport,
                OverloadProcessingFactoryInventory.SLOT_OUTPUT_0,
                OverloadProcessingFactoryInventory.OUTPUT_SLOT_COUNT,
                inventory::getStackInSlot);
    }
}
