package com.moakiee.ae2lt.blockentity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.moakiee.ae2lt.machine.firmament.FirmamentConversionInventory;

class FirmamentNaturalLootContractTest {
    @BeforeAll
    static void bootstrapMinecraft() {
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
    void naturalLootUsesTheOutputInsertionPath() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/blockentity/FirmamentConversionCoreBlockEntity.java"));
        String method = source.substring(
                source.indexOf("public void initializeNaturalLoot("),
                source.indexOf("private Optional<FirmamentConversionLockedRecipe> lockCurrentRecipe("));

        assertTrue(method.contains("inventory.insertRecipeOutput("),
                "Natural loot must bypass the input-only slot validation through the output API");
        assertFalse(method.contains("inventory.setStackInSlot("),
                "Validated direct writes reject every output slot");
    }
}
