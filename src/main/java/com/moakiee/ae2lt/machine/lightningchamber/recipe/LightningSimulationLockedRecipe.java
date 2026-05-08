package com.moakiee.ae2lt.machine.lightningchamber.recipe;

import java.util.Arrays;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.machine.lightningchamber.LightningSimulationChamberInventory;

public final class LightningSimulationLockedRecipe {
    private static final String TAG_RECIPE_ID = "RecipeId";
    private static final String TAG_RESULT = "Result";
    private static final String TAG_TOTAL_ENERGY = "TotalEnergy";
    private static final String TAG_LIGHTNING_COST = "LightningCost";
    private static final String TAG_LIGHTNING_TIER = "LightningTier";
    private static final String TAG_LEGACY_DUST_COST = "DustCost";
    private static final String TAG_INPUTS = "InputConsumptions";

    private final Identifier recipeId;
    private final ItemStack result;
    private final long totalEnergy;
    private final int lightningCost;
    private final LightningKey.Tier lightningTier;
    private final int[] inputConsumptions;

    public LightningSimulationLockedRecipe(
            Identifier recipeId,
            ItemStack result,
            long totalEnergy,
            int lightningCost,
            LightningKey.Tier lightningTier,
            int[] inputConsumptions) {
        this.recipeId = Objects.requireNonNull(recipeId, "recipeId");
        this.result = Objects.requireNonNull(result, "result").copy();
        this.totalEnergy = totalEnergy;
        this.lightningCost = lightningCost;
        this.lightningTier = Objects.requireNonNull(lightningTier, "lightningTier");
        if (result.isEmpty()) {
            throw new IllegalArgumentException("result cannot be empty");
        }
        if (totalEnergy <= 0) {
            throw new IllegalArgumentException("totalEnergy must be positive");
        }
        if (lightningCost <= 0) {
            throw new IllegalArgumentException("lightningCost must be positive");
        }
        if (inputConsumptions.length != 3) {
            throw new IllegalArgumentException("inputConsumptions must have length 3");
        }
        this.inputConsumptions = Arrays.copyOf(inputConsumptions, inputConsumptions.length);
    }

    public static LightningSimulationLockedRecipe fromCandidate(LightningSimulationRecipeCandidate candidate) {
        RecipeHolder<LightningSimulationRecipe> holder = candidate.recipe();
        return new LightningSimulationLockedRecipe(
                holder.id().identifier(),
                holder.value().getResultStack(),
                holder.value().totalEnergy(),
                holder.value().lightningCost(),
                holder.value().lightningTier(),
                candidate.match().inputConsumptions());
    }

    public Identifier recipeId() {
        return recipeId;
    }

    public ItemStack result() {
        return result.copy();
    }

    public long totalEnergy() {
        return totalEnergy;
    }

    public int lightningCost() {
        return lightningCost;
    }

    public LightningKey.Tier lightningTier() {
        return lightningTier;
    }

    public int[] inputConsumptions() {
        return Arrays.copyOf(inputConsumptions, inputConsumptions.length);
    }

    public int inputConsumptionForSlot(int slot) {
        if (slot < LightningSimulationChamberInventory.SLOT_INPUT_0
                || slot > LightningSimulationChamberInventory.SLOT_INPUT_2) {
            throw new IllegalArgumentException("slot must be one of the three input slots");
        }
        return inputConsumptions[slot];
    }

    public void writeTo(ValueOutput data) {
        data.putString(TAG_RECIPE_ID, recipeId.toString());
        data.child(TAG_RESULT).store(ItemStack.MAP_CODEC, result);
        data.putLong(TAG_TOTAL_ENERGY, totalEnergy);
        data.putInt(TAG_LIGHTNING_COST, lightningCost);
        data.putString(TAG_LIGHTNING_TIER, lightningTier.getSerializedName());
        data.putIntArray(TAG_INPUTS, Arrays.copyOf(inputConsumptions, inputConsumptions.length));
    }

    @Nullable
    public static LightningSimulationLockedRecipe fromInput(ValueInput data) {
        ItemStack result = data.child(TAG_RESULT)
                .flatMap(resultTag -> resultTag.read(ItemStack.MAP_CODEC))
                .orElse(ItemStack.EMPTY);
        int[] inputConsumptions = data.getIntArray(TAG_INPUTS).orElse(new int[0]);
        long totalEnergy = data.getLongOr(TAG_TOTAL_ENERGY, 0L);
        int lightningCost = data.getInt(TAG_LIGHTNING_COST)
                .orElseGet(() -> data.getIntOr(TAG_LEGACY_DUST_COST, 0) > 0
                        ? LightningSimulationRecipe.DEFAULT_LIGHTNING_COST
                        : 0);
        LightningKey.Tier lightningTier = LightningKey.Tier.fromSerializedName(
                data.getStringOr(
                        TAG_LIGHTNING_TIER,
                        LightningSimulationRecipe.DEFAULT_LIGHTNING_TIER.getSerializedName()));
        return createOrNull(
                data.getStringOr(TAG_RECIPE_ID, ""),
                result,
                totalEnergy,
                lightningCost,
                lightningTier,
                inputConsumptions);
    }

    @Nullable
    private static LightningSimulationLockedRecipe createOrNull(
            String recipeId,
            ItemStack result,
            long totalEnergy,
            int lightningCost,
            LightningKey.Tier lightningTier,
            int[] inputConsumptions) {
        if (recipeId.isEmpty()
                || result.isEmpty()
                || totalEnergy <= 0
                || lightningCost <= 0
                || inputConsumptions.length != 3) {
            return null;
        }

        try {
            return new LightningSimulationLockedRecipe(
                    Identifier.parse(recipeId),
                    result,
                    totalEnergy,
                    lightningCost,
                    lightningTier,
                    inputConsumptions);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
