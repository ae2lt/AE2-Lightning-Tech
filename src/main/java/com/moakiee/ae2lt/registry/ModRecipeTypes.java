package com.moakiee.ae2lt.registry;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.lightning.LightningTransformRecipe;
import com.moakiee.ae2lt.lightning.strike.LightningStrikeRecipe;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalCatalyzerRecipe;
import com.moakiee.ae2lt.machine.firmament.recipe.FirmamentConversionRecipe;
import com.moakiee.ae2lt.machine.lightningassembly.recipe.LightningAssemblyRecipe;
import com.moakiee.ae2lt.machine.lightningchamber.recipe.LightningSimulationRecipe;
import com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingRecipe;
import com.moakiee.ae2lt.recipe.CreativePigmeeDuplicationRecipe;
import com.moakiee.ae2lt.recipe.HyperdimensionalPigmeeConversionRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModRecipeTypes {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, AE2LightningTech.MODID);
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, AE2LightningTech.MODID);

    public static final RegistryObject<RecipeSerializer<LightningTransformRecipe>>
            LIGHTNING_TRANSFORM_SERIALIZER =
                    RECIPE_SERIALIZERS.register("lightning_transform", LightningTransformRecipe.Serializer::new);

    public static final RegistryObject<RecipeType<LightningTransformRecipe>> LIGHTNING_TRANSFORM_TYPE =
            RECIPE_TYPES.register(
                    "lightning_transform",
                    () -> RecipeType.simple(new ResourceLocation(AE2LightningTech.MODID, "lightning_transform")));

    public static final RegistryObject<RecipeSerializer<LightningSimulationRecipe>>
            LIGHTNING_SIMULATION_SERIALIZER =
                    RECIPE_SERIALIZERS.register("lightning_simulation", LightningSimulationRecipe.Serializer::new);

    public static final RegistryObject<RecipeType<LightningSimulationRecipe>> LIGHTNING_SIMULATION_TYPE =
            RECIPE_TYPES.register(
                    "lightning_simulation",
                    () -> RecipeType.simple(new ResourceLocation(AE2LightningTech.MODID, "lightning_simulation")));

    public static final RegistryObject<RecipeSerializer<LightningAssemblyRecipe>>
            LIGHTNING_ASSEMBLY_SERIALIZER =
                    RECIPE_SERIALIZERS.register("lightning_assembly", LightningAssemblyRecipe.Serializer::new);

    public static final RegistryObject<RecipeType<LightningAssemblyRecipe>> LIGHTNING_ASSEMBLY_TYPE =
            RECIPE_TYPES.register(
                    "lightning_assembly",
                    () -> RecipeType.simple(new ResourceLocation(AE2LightningTech.MODID, "lightning_assembly")));

    public static final RegistryObject<RecipeSerializer<OverloadProcessingRecipe>>
            OVERLOAD_PROCESSING_SERIALIZER =
                    RECIPE_SERIALIZERS.register("overload_processing", OverloadProcessingRecipe.Serializer::new);

    public static final RegistryObject<RecipeType<OverloadProcessingRecipe>> OVERLOAD_PROCESSING_TYPE =
            RECIPE_TYPES.register(
                    "overload_processing",
                    () -> RecipeType.simple(new ResourceLocation(AE2LightningTech.MODID, "overload_processing")));

    public static final RegistryObject<RecipeSerializer<CrystalCatalyzerRecipe>>
            CRYSTAL_CATALYZER_SERIALIZER =
                    RECIPE_SERIALIZERS.register("crystal_catalyzer", CrystalCatalyzerRecipe.Serializer::new);

    public static final RegistryObject<RecipeSerializer<LightningStrikeRecipe>>
            LIGHTNING_STRIKE_SERIALIZER =
                    RECIPE_SERIALIZERS.register("lightning_strike", LightningStrikeRecipe.Serializer::new);

    public static final RegistryObject<RecipeType<LightningStrikeRecipe>> LIGHTNING_STRIKE_TYPE =
            RECIPE_TYPES.register(
                    "lightning_strike",
                    () -> RecipeType.simple(new ResourceLocation(AE2LightningTech.MODID, "lightning_strike")));

    public static final RegistryObject<RecipeType<CrystalCatalyzerRecipe>> CRYSTAL_CATALYZER_TYPE =
            RECIPE_TYPES.register(
                    "crystal_catalyzer",
                    () -> RecipeType.simple(new ResourceLocation(AE2LightningTech.MODID, "crystal_catalyzer")));

    public static final RegistryObject<RecipeSerializer<FirmamentConversionRecipe>>
            FIRMAMENT_CONVERSION_SERIALIZER =
                    RECIPE_SERIALIZERS.register(
                            "firmament_conversion", FirmamentConversionRecipe.Serializer::new);

    public static final RegistryObject<RecipeSerializer<HyperdimensionalPigmeeConversionRecipe>>
            HYPERDIMENSIONAL_PIGMEE_CONVERSION_SERIALIZER =
                    RECIPE_SERIALIZERS.register(
                            "hyperdimensional_pigmee_conversion",
                            () -> new SimpleCraftingRecipeSerializer<>(
                                    HyperdimensionalPigmeeConversionRecipe::new));

    public static final RegistryObject<RecipeType<FirmamentConversionRecipe>>
            FIRMAMENT_CONVERSION_TYPE =
                    RECIPE_TYPES.register(
                            "firmament_conversion",
                            () -> RecipeType.simple(new ResourceLocation(AE2LightningTech.MODID, "firmament_conversion")));

    public static final RegistryObject<RecipeSerializer<CreativePigmeeDuplicationRecipe>>
            CREATIVE_PIGMEE_DUPLICATION_SERIALIZER =
                    RECIPE_SERIALIZERS.register(
                            "creative_pigmee_duplication",
                            () -> new SimpleCraftingRecipeSerializer<>(
                                    CreativePigmeeDuplicationRecipe::new));

    private ModRecipeTypes() {
    }
}

