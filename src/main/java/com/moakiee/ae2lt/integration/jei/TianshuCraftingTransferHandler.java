package com.moakiee.ae2lt.integration.jei;

import appeng.integration.modules.itemlists.CraftingHelper;
import appeng.integration.modules.itemlists.TransferHelper;
import appeng.menu.me.items.CraftingTermMenu;
import com.moakiee.ae2lt.menu.TianshuCraftingTermMenu;
import java.util.Objects;
import java.util.Optional;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IUniversalRecipeTransferHandler;
import mezz.jei.api.recipe.vanilla.IJeiAnvilRecipe;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import org.jetbrains.annotations.Nullable;

/** Direct JEI API integration, also available without the separate AE2 JEI addon. */
public final class TianshuCraftingTransferHandler<M extends TianshuCraftingTermMenu> implements IUniversalRecipeTransferHandler<M> {
    private final Class<M> menuClass;
    @Nullable private final MenuType<M> menuType;
    private final IRecipeTransferHandlerHelper helper;
    public TianshuCraftingTransferHandler(Class<M> menuClass, @Nullable MenuType<M> menuType, IRecipeTransferHandlerHelper helper) {
        this.menuClass = menuClass; this.menuType = menuType; this.helper = helper;
    }
    @Override public Class<M> getContainerClass() { return menuClass; }
    @Override public Optional<MenuType<M>> getMenuType() { return Optional.ofNullable(menuType); }
    @Nullable @Override public IRecipeTransferError transferRecipe(M menu, Object displayedRecipe, IRecipeSlotsView slots, Player player, boolean maxTransfer, boolean doTransfer) {
        if (!menu.canUseWorkstations()) return helper.createInternalError();
        if (displayedRecipe instanceof IJeiAnvilRecipe anvil) {
            var inputs = slots.getSlotViews(RecipeIngredientRole.INPUT);
            var left = inputs.isEmpty() ? ItemStack.EMPTY : inputs.getFirst().getDisplayedItemStack().orElse(ItemStack.EMPTY);
            var right = inputs.size() < 2 ? ItemStack.EMPTY : inputs.get(1).getDisplayedItemStack().orElse(ItemStack.EMPTY);
            if (left.isEmpty()) left = anvil.getLeftInputs().stream().filter(stack -> !stack.isEmpty()).findFirst().orElse(ItemStack.EMPTY);
            if (right.isEmpty()) right = anvil.getRightInputs().stream().filter(stack -> !stack.isEmpty()).findFirst().orElse(ItemStack.EMPTY);
            boolean craftMissing = AbstractContainerScreen.hasControlDown();
            var error = workFeedback(menu.getAnvilRecipeAvailability(left, right), slots, craftMissing, doTransfer);
            if (error == null && doTransfer) menu.fillAnvilRecipe(left, right, craftMissing);
            return error;
        }
        if (!(displayedRecipe instanceof RecipeHolder<?> holder)) return helper.createInternalError();
        if (holder.value() instanceof CraftingRecipe crafting) {
            if (!crafting.canCraftInDimensions(3, 3)) return helper.createUserErrorWithTooltip(Component.translatable("ae2lt.tianshu.work.recipe_too_large"));
            var recipe = new RecipeHolder<>(holder.id(), crafting);
            var ingredients = helper.getGuiSlotIndexToIngredientMap(recipe);
            if (ingredients.isEmpty()) return helper.createInternalError();
            var missing = menu.findMissingIngredients(ingredients);
            if (missing.missingSlots().size() == ingredients.size()) {
                var inputSlots = slots.getSlotViews(RecipeIngredientRole.INPUT);
                var missingViews = missing.missingSlots().stream()
                        .map(index -> index >= 0 && index < inputSlots.size() ? inputSlots.get(index) : null)
                        .filter(Objects::nonNull).toList();
                return helper.createUserErrorForMissingSlots(
                        Component.translatable("ae2lt.tianshu.work.no_materials"), missingViews);
            }
            boolean craftMissing = AbstractContainerScreen.hasControlDown();
            if (doTransfer) {
                menu.prepareCraftingTransfer();
                CraftingHelper.performTransfer(menu, holder.id(), crafting, craftMissing);
            } else if (missing.anyMissingOrCraftable()) {
                return new CraftingFeedback(missing, craftMissing);
            }
            return null;
        }
        if (!(holder.value() instanceof SmithingRecipe || holder.value() instanceof StonecutterRecipe)) return helper.createInternalError();
        boolean craftMissing = AbstractContainerScreen.hasControlDown();
        var error = workFeedback(menu.getWorkRecipeAvailability(holder.value()), slots, craftMissing, doTransfer);
        if (error == null && doTransfer) menu.fillWorkRecipe(holder.id().toString(), craftMissing);
        return error;
    }

    @Nullable private IRecipeTransferError workFeedback(TianshuCraftingTermMenu.WorkRecipeAvailability available,
            IRecipeSlotsView slots, boolean craftMissing, boolean doTransfer) {
        if (available.requiredSlots() == 0) return helper.createInternalError();
        var missing = available.missing();
        if (!available.canTransfer()) {
            var inputs = slots.getSlotViews(RecipeIngredientRole.INPUT);
            var missingViews = missing.missingSlots().stream()
                    .filter(index -> index >= 0 && index < inputs.size()).map(inputs::get).toList();
            return helper.createUserErrorForMissingSlots(Component.translatable("ae2lt.tianshu.work.no_materials"), missingViews);
        }
        return !doTransfer && missing.anyMissingOrCraftable() ? new CraftingFeedback(missing, craftMissing) : null;
    }

    /** Keep partial transfers clickable while exposing AE2's missing/autocraft distinction to JEI. */
    private record CraftingFeedback(CraftingTermMenu.MissingIngredientSlots ingredients, boolean craftMissing)
            implements IRecipeTransferError {
        @Override public Type getType() { return Type.COSMETIC; }
        @Override public int getButtonHighlightColor() {
            return ingredients.anyMissing() ? TransferHelper.ORANGE_PLUS_BUTTON_COLOR
                    : TransferHelper.BLUE_PLUS_BUTTON_COLOR;
        }
        @Override public int getMissingCountHint() { return ingredients.missingSlots().size(); }
        @Override public void getTooltip(ITooltipBuilder tooltip) {
            tooltip.addAll(TransferHelper.createCraftingTooltip(ingredients, craftMissing, true));
        }
        @Override public void showError(GuiGraphics graphics, int mouseX, int mouseY,
                IRecipeSlotsView slots, int recipeX, int recipeY) {
            var pose = graphics.pose();
            pose.pushPose();
            pose.translate(recipeX, recipeY, 0);
            var inputs = slots.getSlotViews(RecipeIngredientRole.INPUT);
            for (int index = 0; index < inputs.size(); index++) {
                if (ingredients.missingSlots().contains(index)) {
                    inputs.get(index).drawHighlight(graphics, TransferHelper.RED_SLOT_HIGHLIGHT_COLOR);
                } else if (ingredients.craftableSlots().contains(index)) {
                    inputs.get(index).drawHighlight(graphics, TransferHelper.BLUE_SLOT_HIGHLIGHT_COLOR);
                }
            }
            pose.popPose();
        }
    }
}
