package com.moakiee.ae2lt.machine.overloadfactory.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.fml.loading.LoadingModList;

import com.moakiee.ae2lt.machine.overloadfactory.OverloadProcessingFactoryInventory;
import com.moakiee.ae2lt.logic.AdjacentItemAutoExportHelper;
import com.moakiee.ae2lt.me.key.LightningKey;

class OverloadProcessingRecipeMatchCacheTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void reusesInputSnapshotAndPreparedPlanWhileExactItemsStayUnchanged() {
        var inventory = inventoryWithIron(8);
        var cache = new OverloadProcessingRecipeMatchCache();
        var holder = recipeHolder("stable", recipe());

        var firstInput = cache.snapshot(inventory, FluidStack.EMPTY);
        var firstPlan = cache.prepare(holder, firstInput).orElseThrow();
        var secondInput = cache.snapshot(inventory, FluidStack.EMPTY);
        var secondPlan = cache.prepare(holder, secondInput).orElseThrow();

        assertSame(firstInput, secondInput);
        assertSame(firstPlan, secondPlan);
        assertEquals(4L, secondPlan.maxOperationsByAvailability());
    }

    @Test
    void detectsCountAndComponentMutationsEvenWithoutInventoryNotifications() {
        var inventory = inventoryWithIron(8);
        var cache = new OverloadProcessingRecipeMatchCache();
        var holder = recipeHolder("mutated", recipe());

        var originalInput = cache.snapshot(inventory, FluidStack.EMPTY);
        var originalPlan = cache.prepare(holder, originalInput).orElseThrow();

        // Deliberately mutate the live stack reference, bypassing the handler's
        // normal change callback. Cache validation must still detect it.
        inventory.getStackInSlot(0).grow(2);
        var countChangedInput = cache.snapshot(inventory, FluidStack.EMPTY);
        var countChangedPlan = cache.prepare(holder, countChangedInput).orElseThrow();
        assertNotSame(originalInput, countChangedInput);
        assertNotSame(originalPlan, countChangedPlan);
        assertEquals(5L, countChangedPlan.maxOperationsByAvailability());

        inventory.getStackInSlot(0).set(DataComponents.CUSTOM_NAME, Component.literal("changed"));
        var componentChangedInput = cache.snapshot(inventory, FluidStack.EMPTY);
        var componentChangedPlan = cache.prepare(holder, componentChangedInput).orElseThrow();
        assertNotSame(countChangedInput, componentChangedInput);
        assertNotSame(countChangedPlan, componentChangedPlan);
    }

    @Test
    void fluidChangesStayDynamicWithoutDiscardingItemMatchPlan() {
        var inventory = inventoryWithIron(8);
        var cache = new OverloadProcessingRecipeMatchCache();
        var holder = recipeHolder("fluid", recipe());

        var firstInput = cache.snapshot(inventory, new FluidStack(Fluids.WATER, 1_000));
        var firstPlan = cache.prepare(holder, firstInput).orElseThrow();
        var secondInput = cache.snapshot(inventory, new FluidStack(Fluids.WATER, 250));
        var secondPlan = cache.prepare(holder, secondInput).orElseThrow();

        assertNotSame(firstInput, secondInput);
        assertEquals(250, secondInput.inputFluid().getAmount());
        assertSame(firstPlan, secondPlan);
    }

    @Test
    void replacingRecipeObjectUnderSameIdInvalidatesPreparedPlan() {
        var inventory = inventoryWithIron(8);
        var cache = new OverloadProcessingRecipeMatchCache();
        var input = cache.snapshot(inventory, FluidStack.EMPTY);
        var firstHolder = recipeHolder("reload", recipe());
        var reloadedHolder = recipeHolder("reload", recipe());

        var firstPlan = cache.prepare(firstHolder, input).orElseThrow();
        var reloadedPlan = cache.prepare(reloadedHolder, input).orElseThrow();

        assertNotSame(firstHolder.value(), reloadedHolder.value());
        assertNotSame(firstPlan, reloadedPlan);
    }

    @Test
    void tagReloadInvalidatesInputSnapshotAndPreparedPlans() {
        var inventory = inventoryWithIron(8);
        var cache = new OverloadProcessingRecipeMatchCache();
        var holder = recipeHolder("tags", recipe());

        var originalInput = cache.snapshot(inventory, FluidStack.EMPTY);
        var originalPlan = cache.prepare(holder, originalInput).orElseThrow();
        OverloadProcessingRecipeMatchCache.invalidateAllForReload();
        var reloadedInput = cache.snapshot(inventory, FluidStack.EMPTY);
        var reloadedPlan = cache.prepare(holder, reloadedInput).orElseThrow();

        assertNotSame(originalInput, reloadedInput);
        assertNotSame(originalPlan, reloadedPlan);
    }

    @Test
    void outputProbeDistinguishesEmptyAndPopulatedOutputSlot() {
        var inventory = new OverloadProcessingFactoryInventory(null);

        assertFalse(hasItemOutput(inventory));
        assertTrue(inventory.insertRecipeOutputs(List.of(new ItemStack(Items.GOLD_INGOT))));
        assertTrue(hasItemOutput(inventory));
    }

    private static OverloadProcessingFactoryInventory inventoryWithIron(int count) {
        var inventory = new OverloadProcessingFactoryInventory(null);
        inventory.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, count));
        return inventory;
    }

    private static boolean hasItemOutput(OverloadProcessingFactoryInventory inventory) {
        return AdjacentItemAutoExportHelper.hasAnyOutput(
                true,
                OverloadProcessingFactoryInventory.SLOT_OUTPUT_0,
                OverloadProcessingFactoryInventory.OUTPUT_SLOT_COUNT,
                inventory::getStackInSlot);
    }

    private static RecipeHolder<OverloadProcessingRecipe> recipeHolder(
            String path,
            OverloadProcessingRecipe recipe) {
        return new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath("ae2lt_test", path), recipe);
    }

    private static OverloadProcessingRecipe recipe() {
        return new OverloadProcessingRecipe(
                0,
                List.of(new OverloadProcessingIngredient(Ingredient.of(Items.IRON_INGOT), 2)),
                FluidStack.EMPTY,
                List.of(new ItemStack(Items.GOLD_INGOT)),
                FluidStack.EMPTY,
                100L,
                4,
                LightningKey.Tier.HIGH_VOLTAGE);
    }
}
