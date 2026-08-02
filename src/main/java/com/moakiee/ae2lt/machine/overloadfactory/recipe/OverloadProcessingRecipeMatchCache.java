package com.moakiee.ae2lt.machine.overloadfactory.recipe;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.fluids.FluidStack;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.machine.overloadfactory.OverloadProcessingFactoryInventory;

/**
 * Per-factory cache for the operation-independent part of recipe matching.
 *
 * <p>The cache deliberately excludes lightning availability, output capacity,
 * matrix parallelism and every other externally mutable condition. Those are
 * still evaluated for every lookup. Only the exact input-slot snapshot and the
 * resulting {@link OverloadProcessingRecipe.MatchPlan} instances are reused.</p>
 */
@EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class OverloadProcessingRecipeMatchCache {
    private static volatile long reloadGeneration;

    private final Map<ResourceLocation, PreparedMatch> preparedMatches = new HashMap<>();

    private OverloadProcessingRecipeInput cachedInput;
    private long observedReloadGeneration = reloadGeneration;

    public OverloadProcessingRecipeInput snapshot(
            OverloadProcessingFactoryInventory inventory,
            FluidStack inputFluid) {
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(inputFluid, "inputFluid");

        discardReloadedRecipes();
        if (cachedInput == null || !sameInventoryItems(cachedInput.slotStacks(), inventory)) {
            cachedInput = OverloadProcessingRecipeInput.fromInventory(inventory, inputFluid);
            preparedMatches.clear();
            return cachedInput;
        }

        if (!sameFluid(cachedInput.inputFluid(), inputFluid)) {
            // MatchPlan only depends on item slots, so a fluid-only change must
            // update the lookup snapshot without discarding the prepared plans.
            cachedInput = new OverloadProcessingRecipeInput(cachedInput.slotStacks(), inputFluid.copy());
        }
        return cachedInput;
    }

    public Optional<OverloadProcessingRecipe.MatchPlan> prepare(
            RecipeHolder<OverloadProcessingRecipe> holder,
            OverloadProcessingRecipeInput input) {
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(input, "input");

        discardReloadedRecipes();
        OverloadProcessingRecipe recipe = holder.value();
        if (input != cachedInput) {
            // Only snapshots issued by this cache may use its prepared plans.
            // Preserve correctness for accidental external callers by falling
            // back to an uncached calculation.
            return recipe.prepareMatch(input);
        }
        PreparedMatch cached = preparedMatches.get(holder.id());
        if (cached != null && cached.recipe() == recipe) {
            return cached.match();
        }

        Optional<OverloadProcessingRecipe.MatchPlan> prepared = recipe.prepareMatch(input);
        preparedMatches.put(holder.id(), new PreparedMatch(recipe, prepared));
        return prepared;
    }

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        invalidateAllForReload();
    }

    static void invalidateAllForReload() {
        reloadGeneration++;
    }

    private void discardReloadedRecipes() {
        long currentGeneration = reloadGeneration;
        if (observedReloadGeneration == currentGeneration) {
            return;
        }
        cachedInput = null;
        preparedMatches.clear();
        observedReloadGeneration = currentGeneration;
    }

    private static boolean sameInventoryItems(
            List<OverloadProcessingRecipeInput.SlotStack> cachedSlots,
            OverloadProcessingFactoryInventory inventory) {
        int cachedIndex = 0;
        for (int slot = OverloadProcessingFactoryInventory.SLOT_INPUT_0;
             slot <= OverloadProcessingFactoryInventory.SLOT_INPUT_8;
             slot++) {
            ItemStack current = inventory.getStackInSlot(slot);
            if (current.isEmpty()) {
                if (cachedIndex < cachedSlots.size() && cachedSlots.get(cachedIndex).slot() == slot) {
                    return false;
                }
                continue;
            }

            if (cachedIndex >= cachedSlots.size()) {
                return false;
            }
            var cached = cachedSlots.get(cachedIndex);
            if (cached.slot() != slot
                    || cached.stack().getCount() != current.getCount()
                    || !ItemStack.isSameItemSameComponents(cached.stack(), current)) {
                return false;
            }
            cachedIndex++;
        }
        return cachedIndex == cachedSlots.size();
    }

    private static boolean sameFluid(FluidStack first, FluidStack second) {
        if (first.isEmpty() || second.isEmpty()) {
            return first.isEmpty() && second.isEmpty();
        }
        return first.getAmount() == second.getAmount()
                && FluidStack.isSameFluidSameComponents(first, second);
    }

    private record PreparedMatch(
            OverloadProcessingRecipe recipe,
            Optional<OverloadProcessingRecipe.MatchPlan> match) {
    }
}
