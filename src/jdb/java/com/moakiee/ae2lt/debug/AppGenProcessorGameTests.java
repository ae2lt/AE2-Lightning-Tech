package com.moakiee.ae2lt.debug;

import appeng.recipes.handlers.InscriberRecipe;
import com.google.gson.JsonParser;
import com.moakiee.ae2lt.machine.overloadfactory.OverloadProcessingFactoryInventory;
import com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingRecipe;
import com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingRecipeInput;
import com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingRecipeService;
import com.moakiee.ae2lt.me.key.LightningKey;
import io.netty.buffer.Unpooled;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Uses the actual optional mod's items and recipe manager, not mock registry entries. */
@GameTestHolder("ae2lt")
@PrefixGameTestTemplate(false)
public final class AppGenProcessorGameTests {
    private static final ResourceLocation BULK = ResourceLocation.parse("ae2lt:overload_processing/appgen_origination_processor");

    @GameTest(template = "pigmee_station_empty")
    public static void originationProcessorLoadsMatchesAndSyncsOnlyWithAppGen(GameTestHelper helper) {
        var level = helper.getLevel();
        var manager = level.getRecipeManager();
        var bulk = manager.byKey(BULK);
        if (!ModList.get().isLoaded("appgen")) {
            helper.assertTrue(bulk.isEmpty(), "AppGen recipe must be excluded when the optional mod is absent");
            helper.succeed();
            return;
        }
        var ember = item("appgen:ember_crystal");
        var emberBlock = item("appgen:ember_block");
        var printed = item("appgen:printed_origination_processor");
        var processor = item("appgen:origination_processor");
        var compression = (ShapedRecipe) manager.byKey(ResourceLocation.parse("appgen:crafting/ember_block"))
                .orElseThrow().value();
        helper.assertTrue(compression.getWidth() == 2 && compression.getHeight() == 2
                        && compression.getIngredients().stream().allMatch(i -> i.test(ember))
                        && compression.getResultItem(level.registryAccess()).is(emberBlock.getItem()),
                "actual AppGen compression is four crystals per ember block");
        var printing = (InscriberRecipe) manager.byKey(ResourceLocation.parse("appgen:inscriber/printed_origination_processor"))
                .orElseThrow().value();
        var finishing = (InscriberRecipe) manager.byKey(ResourceLocation.parse("appgen:inscriber/origination_processor"))
                .orElseThrow().value();
        helper.assertTrue(printing.getMiddleInput().test(ember) && printing.getResultItem(level.registryAccess()).is(printed.getItem())
                        && finishing.getTopOptional().test(printed) && finishing.getResultItem(level.registryAccess()).is(processor.getItem()),
                "bulk output follows the real inscriber processor chain");
        helper.assertTrue(bulk.isPresent() && bulk.get().value() instanceof OverloadProcessingRecipe, "conditional bulk recipe loaded");
        var recipe = (OverloadProcessingRecipe) bulk.orElseThrow().value();
        helper.assertTrue(recipe.itemInputs().size() == 3 && recipe.itemInputs().get(0).count() == 9
                        && recipe.itemInputs().get(1).count() == 4 && recipe.itemInputs().get(2).count() == 4,
                "36 processors require 9 ember blocks, 4 redstone blocks, and 4 silicon blocks");
        helper.assertTrue(recipe.totalEnergy() == 400_000 && recipe.lightningCost() == 1
                        && recipe.lightningTier() == LightningKey.Tier.HIGH_VOLTAGE,
                "400000 FE and 1 high-voltage lightning match other bulk processors");
        helper.assertTrue(recipe.itemResults().size() == 1 && recipe.itemResults().getFirst().is(processor.getItem())
                        && recipe.itemResults().getFirst().getCount() == 36, "exactly 36 actual AppGen processors");
        var siliconOptions = recipe.itemInputs().get(2).ingredient().getItems();
        helper.assertTrue(siliconOptions.length > 0, "silicon block tag resolves with Applied Flux present");
        var inventory = new OverloadProcessingFactoryInventory(null);
        inventory.setStackInSlot(0, emberBlock.copyWithCount(9));
        inventory.setStackInSlot(1, new ItemStack(Items.REDSTONE_BLOCK, 4));
        inventory.setStackInSlot(2, siliconOptions[0].copyWithCount(4));
        var input = OverloadProcessingRecipeInput.fromInventory(inventory, FluidStack.EMPTY);
        helper.assertTrue(recipe.matches(input, level), "real tagged inputs match");
        var candidate = OverloadProcessingRecipeService.findFirstProcessable(level, inventory,
                FluidStack.EMPTY, FluidStack.EMPTY, 1, 0);
        helper.assertTrue(candidate.isPresent() && candidate.get().recipe().id().equals(BULK)
                        && candidate.get().parallel() == 1, "factory recipe selection accepts the new processor");
        helper.assertTrue(OverloadProcessingRecipeService.findFirstProcessable(level, inventory,
                FluidStack.EMPTY, FluidStack.EMPTY, 0, 0).isEmpty(), "lightning requirement is retained");
        inventory.setStackInSlot(0, emberBlock.copyWithCount(8));
        helper.assertTrue(OverloadProcessingRecipeService.findFirstProcessable(level, inventory,
                FluidStack.EMPTY, FluidStack.EMPTY, 1, 0).isEmpty(), "eight ember blocks are insufficient");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        try {
            var codec = new OverloadProcessingRecipe.Serializer().streamCodec();
            codec.encode(buffer, recipe);
            var copy = codec.decode(buffer);
            helper.assertTrue(copy.matches(input, level) && copy.totalEnergy() == 400_000
                            && copy.itemResults().getFirst().is(processor.getItem())
                            && copy.itemResults().getFirst().getCount() == 36,
                    "client synchronization preserves the optional recipe, ingredients, energy, and result");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    private static ItemStack item(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).orElseThrow());
    }

    @GameTest(template = "pigmee_station_empty")
    public static void emberSynthesisPreservesUpstreamReaction(GameTestHelper helper) throws Exception {
        assertReactionRecipe(helper, "ember_crystal");
    }

    @GameTest(template = "pigmee_station_empty")
    public static void emberDuplicationPreservesUpstreamReaction(GameTestHelper helper) throws Exception {
        assertReactionRecipe(helper, "ember_crystal_duplicate");
    }

    @GameTest(template = "pigmee_station_empty")
    public static void emberChargingPreservesUpstreamReaction(GameTestHelper helper) throws Exception {
        assertReactionRecipe(helper, "charged_ember_crystal");
    }

    private static void assertReactionRecipe(GameTestHelper helper, String name) throws Exception {
        var level = helper.getLevel();
        var id = ResourceLocation.parse("ae2lt:overload_processing/appgen_" + name);
        var holder = level.getRecipeManager().byKey(id);
        if (!ModList.get().isLoaded("appgen")) {
            helper.assertTrue(holder.isEmpty(), name + " must be excluded without AppGen");
            helper.succeed();
            return;
        }
        helper.assertTrue(holder.isPresent() && holder.get().value() instanceof OverloadProcessingRecipe,
                name + " must load as an overload recipe");
        var recipe = (OverloadProcessingRecipe) holder.orElseThrow().value();
        // Compare against the actual optional mod's resource, independently of our converted JSON.
        var source = level.getServer().getResourceManager().getResourceOrThrow(
                ResourceLocation.parse("appgen:recipe/reaction/" + name + ".json"));
        try (var reader = source.openAsReader()) {
            var upstream = JsonParser.parseReader(reader).getAsJsonObject();
            var inputs = upstream.getAsJsonArray("input_items");
            helper.assertTrue(recipe.itemInputs().size() == inputs.size(), "upstream ingredient count retained");
            var inventory = new OverloadProcessingFactoryInventory(null);
            for (int slot = 0; slot < inputs.size(); slot++) {
                var entry = inputs.get(slot).getAsJsonObject();
                var stack = item(entry.getAsJsonObject("ingredient").get("item").getAsString())
                        .copyWithCount(entry.get("amount").getAsInt());
                var converted = recipe.itemInputs().get(slot);
                helper.assertTrue(converted.count() == stack.getCount() && converted.ingredient().test(stack),
                        "upstream item and amount retained in slot " + slot);
                inventory.setStackInSlot(slot, stack);
            }
            var upstreamFluid = upstream.getAsJsonObject("input_fluid");
            var fluid = new FluidStack(BuiltInRegistries.FLUID.getOptional(ResourceLocation.parse(
                    upstreamFluid.getAsJsonObject("ingredient").get("fluid").getAsString())).orElseThrow(),
                    upstreamFluid.get("amount").getAsInt());
            helper.assertTrue(FluidStack.matches(recipe.fluidInput(), fluid), "upstream lava amount retained");
            var output = upstream.getAsJsonObject("output");
            var result = item(output.get("id").getAsString()).copyWithCount(output.get("#").getAsInt());
            helper.assertTrue(recipe.itemResults().size() == 1
                            && ItemStack.matches(recipe.itemResults().getFirst(), result) && recipe.fluidResult().isEmpty(),
                    "upstream output and yield retained");
            helper.assertTrue(recipe.totalEnergy() == upstream.get("input_energy").getAsLong()
                            && recipe.lightningCost() == 1 && recipe.lightningTier() == LightningKey.Tier.HIGH_VOLTAGE,
                    "upstream FE retained with the standard one high-voltage lightning cost");
            var candidate = OverloadProcessingRecipeService.findFirstProcessable(level, inventory,
                    fluid, FluidStack.EMPTY, 1, 0);
            helper.assertTrue(candidate.isPresent() && candidate.get().recipe().id().equals(id)
                            && candidate.get().parallel() == 1, "factory selects the reaction with actual registered inputs");
            helper.assertTrue(OverloadProcessingRecipeService.findFirstProcessable(level, inventory,
                    fluid, FluidStack.EMPTY, 0, 0).isEmpty(), "lightning remains required");
            helper.assertTrue(OverloadProcessingRecipeService.findFirstProcessable(level, inventory,
                    new FluidStack(Fluids.WATER, fluid.getAmount()), FluidStack.EMPTY, 1, 0).isEmpty(),
                    "water cannot replace lava");
            helper.assertTrue(OverloadProcessingRecipeService.findFirstProcessable(level, inventory,
                    fluid.copyWithAmount(fluid.getAmount() - 1), FluidStack.EMPTY, 1, 0).isEmpty(),
                    "insufficient lava cannot start a reaction");
            var input = OverloadProcessingRecipeInput.fromInventory(inventory, fluid);
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
            try {
                var codec = new OverloadProcessingRecipe.Serializer().streamCodec();
                codec.encode(buffer, recipe);
                var copy = codec.decode(buffer);
                helper.assertTrue(copy.matches(input, level) && FluidStack.matches(copy.fluidInput(), fluid)
                                && ItemStack.matches(copy.itemResults().getFirst(), result)
                                && copy.totalEnergy() == recipe.totalEnergy()
                                && copy.lightningCost() == 1 && copy.lightningTier() == recipe.lightningTier(),
                        "client synchronization retains ingredients, lava, yield, FE, and lightning");
            } finally {
                buffer.release();
            }
            var first = inventory.getStackInSlot(0);
            inventory.setStackInSlot(0, first.copyWithCount(first.getCount() - 1));
            helper.assertTrue(OverloadProcessingRecipeService.findFirstProcessable(level, inventory,
                    fluid, FluidStack.EMPTY, 1, 0).isEmpty(), "insufficient items cannot start a reaction");
        }
        helper.succeed();
    }
}
