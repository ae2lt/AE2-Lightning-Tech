package com.moakiee.ae2lt.gametest;

import com.google.gson.JsonParser;
import com.moakiee.ae2lt.lightning.CountedIngredient;
import com.moakiee.ae2lt.lightning.LightningTransformRecipe;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalCatalyzerRecipe;
import com.moakiee.ae2lt.machine.firmament.recipe.FirmamentConversionIngredient;
import com.moakiee.ae2lt.machine.firmament.recipe.FirmamentConversionRecipe;
import com.moakiee.ae2lt.machine.lightningassembly.recipe.LightningAssemblyRecipe;
import com.moakiee.ae2lt.machine.lightningchamber.recipe.LightningSimulationIngredient;
import com.moakiee.ae2lt.machine.lightningchamber.recipe.LightningSimulationRecipe;
import com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingIngredient;
import com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingRecipe;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.network.PigmeeAssemblerAnimationPacket;
import com.moakiee.ae2lt.util.LargeStackStreamCodecs;
import com.moakiee.ae2lt.util.LargeStackNbt;
import com.moakiee.ae2lt.machine.lightningassembly.recipe.LightningAssemblyLockedRecipe;
import com.moakiee.ae2lt.machine.lightningchamber.recipe.LightningSimulationLockedRecipe;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalCatalyzerLockedRecipe;
import com.moakiee.ae2lt.machine.firmament.recipe.FirmamentConversionLockedRecipe;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Runs against real Forge registries and ItemStack share-tag serialization. */
@GameTestHolder("ae2lt")
@PrefixGameTestTemplate(false)
public final class LargeStackRecipeSyncGameTests {
    private static final ResourceLocation ID = new ResourceLocation("ae2lt", "large_stack_sync_test");
    private static final int[] COUNTS = {1, 64, 65, 127, 128, 255, 256, 257, 16384, Integer.MAX_VALUE};

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void lockedRecipePersistenceRoundTrip(GameTestHelper helper) {
        var tier = LightningKey.Tier.HIGH_VOLTAGE;
        for (int count : COUNTS) {
            var output = new ItemStack(Items.DIAMOND, count);
            output.getOrCreateTag().putString("marker", "persisted");
            var assembly = new LightningAssemblyLockedRecipe(ID, output, 12345, 8, tier,
                    new int[] {1, 2, 3, 4, 5, 6, 7, 8, 9});
            var assemblyRead = LightningAssemblyLockedRecipe.fromTag(assembly.toTag());
            helper.assertTrue(assemblyRead != null && ItemStack.matches(output, assemblyRead.result())
                    && assembly.toTag().equals(assemblyRead.toTag()), "Assembly persistence: " + count);
            var simulation = new LightningSimulationLockedRecipe(ID, output, 12345, 8, tier, new int[] {1, 2, 3});
            var simulationRead = LightningSimulationLockedRecipe.fromTag(simulation.toTag());
            helper.assertTrue(simulationRead != null && ItemStack.matches(output, simulationRead.result())
                    && simulation.toTag().equals(simulationRead.toTag()), "Simulation persistence: " + count);
            var catalyzer = new CrystalCatalyzerLockedRecipe(ID, output, 100, 3, 8, tier);
            var catalyzerRead = CrystalCatalyzerLockedRecipe.fromTag(catalyzer.toTag());
            helper.assertTrue(catalyzerRead != null && ItemStack.matches(output, catalyzerRead.output())
                    && catalyzer.toTag().equals(catalyzerRead.toTag()), "Catalyzer persistence: " + count);
            var firmament = new FirmamentConversionLockedRecipe(ID,
                    List.of(output, new ItemStack(Items.EMERALD, count)), 53, new int[] {1, 2, 3});
            var firmamentRead = FirmamentConversionLockedRecipe.fromTag(firmament.toTag());
            helper.assertTrue(firmamentRead != null && ItemStack.matches(output, firmamentRead.result())
                    && firmament.toTag().equals(firmamentRead.toTag()), "Firmament persistence: " + count);
            if (count <= 127) {
                var legacy = output.save(new net.minecraft.nbt.CompoundTag());
                helper.assertTrue(ItemStack.matches(output, LargeStackNbt.load(legacy)), "Legacy stack: " + count);
                var oldAssembly = assembly.toTag();
                oldAssembly.put("Result", legacy);
                var oldRead = LightningAssemblyLockedRecipe.fromTag(oldAssembly);
                helper.assertTrue(oldRead != null && ItemStack.matches(output, oldRead.result()), "Legacy recipe");
            }
        }
        helper.assertTrue(LargeStackNbt.load(LargeStackNbt.save(ItemStack.EMPTY)).isEmpty(), "Empty NBT stack");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void allRecipeOutputsRoundTrip(GameTestHelper helper) {
        var ingredient = Ingredient.of(Items.COBBLESTONE);
        var tier = LightningKey.Tier.HIGH_VOLTAGE;
        for (int count : COUNTS) {
            var output = new ItemStack(Items.DIAMOND, count);
            output.getOrCreateTag().putString("recipe_marker", "issue_35");
            roundTrip(helper, new OverloadProcessingRecipe(ID, 7,
                    List.of(new OverloadProcessingIngredient(ingredient, 256)),
                    new FluidStack(Fluids.WATER, 2000), List.of(output),
                    new FluidStack(Fluids.LAVA, 1000), 12345, 8, tier),
                    new OverloadProcessingRecipe.Serializer(), output);
            var inputs = List.of(new LightningSimulationIngredient(ingredient, 256));
            roundTrip(helper, new LightningSimulationRecipe(ID, 7, inputs, output, 12345, 8, tier),
                    new LightningSimulationRecipe.Serializer(), output);
            roundTrip(helper, new LightningAssemblyRecipe(ID, 7, inputs, output, 12345, 8, tier),
                    new LightningAssemblyRecipe.Serializer(), output);
            roundTrip(helper, new LightningTransformRecipe(ID, 7,
                    List.of(new CountedIngredient(ingredient, 256)), output),
                    new LightningTransformRecipe.Serializer(), output);
            roundTrip(helper, new FirmamentConversionRecipe(7,
                    List.of(new FirmamentConversionIngredient(ingredient, 256)),
                    List.of(output, new ItemStack(Items.EMERALD, count)), 53),
                    new FirmamentConversionRecipe.Serializer(), output);
            roundTrip(helper, new CrystalCatalyzerRecipe(ID, Optional.of(ingredient), 256, output, 100),
                    new CrystalCatalyzerRecipe.Serializer(), output);
            helper.assertTrue(output.getCount() == count, "Encoding must not mutate the original stack");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void datapackFactoryOutputsRoundTrip(GameTestHelper helper) {
        var serializer = new OverloadProcessingRecipe.Serializer();
        for (int count : COUNTS) {
            var json = JsonParser.parseString("""
                    {"inputs":[{"ingredient":{"item":"minecraft:cobblestone"},"count":1}],
                     "results":[{"id":"minecraft:diamond","count":%d}],"totalEnergy":5}
                    """.formatted(count)).getAsJsonObject();
            roundTrip(helper, serializer.fromJson(ID, json), serializer, new ItemStack(Items.DIAMOND, count));
        }
        // Fluid-only recipes legitimately have no item output.
        roundTrip(helper, new OverloadProcessingRecipe(ID, 0, List.of(),
                new FluidStack(Fluids.WATER, 1000), List.of(), new FluidStack(Fluids.LAVA, 1000),
                5, 4, LightningKey.Tier.HIGH_VOLTAGE), serializer, ItemStack.EMPTY);
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void stacksAndAnimationsRoundTrip(GameTestHelper helper) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            LargeStackStreamCodecs.writeItemStack(buffer, ItemStack.EMPTY);
            for (int count : COUNTS) {
                var output = new ItemStack(Items.DIAMOND, count);
                output.getOrCreateTag().putInt("marker", count);
                new PigmeeAssemblerAnimationPacket(new BlockPos(3, 4, 5), (byte) 7, output).write(buffer);
            }
            helper.assertTrue(LargeStackStreamCodecs.readItemStack(buffer).isEmpty(), "Empty stack round trip");
            for (int count : COUNTS) {
                var decoded = PigmeeAssemblerAnimationPacket.decode(buffer);
                helper.assertTrue(decoded.pos().equals(new BlockPos(3, 4, 5)) && decoded.speed() == 7,
                        "Animation metadata must survive");
                helper.assertTrue(decoded.output().is(Items.DIAMOND) && decoded.output().getCount() == count
                        && decoded.output().getOrCreateTag().getInt("marker") == count,
                        "Animation must retain item, count and NBT: " + count);
            }
            helper.assertTrue(!buffer.isReadable(), "All bytes must be consumed");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    private static <T extends Recipe<?>> void roundTrip(
            GameTestHelper helper, T original, RecipeSerializer<T> serializer, ItemStack expected) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        var reencoded = new FriendlyByteBuf(Unpooled.buffer());
        try {
            serializer.toNetwork(buffer, original);
            var decoded = serializer.fromNetwork(ID, buffer);
            helper.assertTrue(ItemStack.matches(expected, decoded.getResultItem(helper.getLevel().registryAccess())),
                    "Output count/item/NBT mismatch: " + original.getClass().getSimpleName() + " / " + expected.getCount());
            helper.assertTrue(!buffer.isReadable(), "Recipe decoder must consume every field");
            serializer.toNetwork(reencoded, decoded);
            buffer.readerIndex(0);
            helper.assertTrue(buffer.equals(reencoded), "All recipe fields must survive re-encoding");
        } finally {
            buffer.release();
            reencoded.release();
        }
    }
}
