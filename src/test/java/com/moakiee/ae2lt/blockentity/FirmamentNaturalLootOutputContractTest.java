package com.moakiee.ae2lt.blockentity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.LoadingModList;

import com.moakiee.ae2lt.machine.firmament.FirmamentConversionInventory;

class FirmamentNaturalLootOutputContractTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void machineOutputPathAcceptsItemsRejectedByExternalOutputInsertion() {
        var inventory = new FirmamentConversionInventory(null);
        var stack = new ItemStack(Items.DIAMOND);

        assertFalse(inventory.isItemValid(FirmamentConversionInventory.SLOT_OUTPUT_0, stack));
        assertTrue(inventory.insertRecipeOutput(stack, false).isEmpty());
        assertEquals(Items.DIAMOND,
                inventory.getStackInSlot(FirmamentConversionInventory.SLOT_OUTPUT_0).getItem());
    }

    @Test
    void naturalLootUsesTheMachineOutputPath() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/blockentity/FirmamentConversionCoreBlockEntity.java"));

        int methodStart = source.indexOf("public void initializeNaturalLoot(RandomSource random)");
        int methodEnd = source.indexOf("private Optional<FirmamentConversionLockedRecipe>", methodStart);
        assertTrue(methodStart >= 0 && methodEnd > methodStart);

        String method = source.substring(methodStart, methodEnd);
        assertTrue(method.contains("inventory.insertRecipeOutput("));
        assertFalse(method.contains("inventory.setStackInSlot("));
    }
}
