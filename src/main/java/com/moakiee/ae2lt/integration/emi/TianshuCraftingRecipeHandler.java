package com.moakiee.ae2lt.integration.emi;

import appeng.integration.modules.emi.EmiUseCraftingRecipeHandler;
import appeng.integration.modules.itemlists.TransferHelper;
import com.moakiee.ae2lt.client.TianshuCraftingTermScreen;
import com.moakiee.ae2lt.menu.TianshuCraftingTermMenu;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.Widget;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.Nullable;

public final class TianshuCraftingRecipeHandler<M extends TianshuCraftingTermMenu> extends EmiUseCraftingRecipeHandler<M> {
    public TianshuCraftingRecipeHandler(Class<M> menuClass) { super(menuClass); }

    @Override public boolean supportsRecipe(EmiRecipe recipe) {
        return super.supportsRecipe(recipe) || recipe.getCategory().equals(VanillaEmiRecipeCategories.SMITHING)
                || recipe.getCategory().equals(VanillaEmiRecipeCategories.STONECUTTING) || isAnvil(recipe);
    }

    private boolean isAnvil(EmiRecipe recipe) {
        return recipe.getCategory().equals(VanillaEmiRecipeCategories.ANVIL_REPAIRING);
    }

    @Nullable private RecipeHolder<?> holder(EmiRecipe recipe, M menu) {
        if (recipe.getBackingRecipe() != null) return recipe.getBackingRecipe();
        return recipe.getId() == null ? null : menu.getPlayer().level().getRecipeManager().byKey(recipe.getId()).orElse(null);
    }

    private record AnvilInputs(ItemStack left, ItemStack right, TianshuCraftingTermMenu.WorkRecipeAvailability available) {}

    @Nullable private AnvilInputs anvilInputs(EmiRecipe recipe, M menu) {
        var inputs = recipe.getInputs();
        if (inputs.size() != 2) return null;
        AnvilInputs best = null;
        int bestScore = Integer.MAX_VALUE;
        for (var leftOption : inputs.getFirst().getEmiStacks()) {
            if (leftOption.getItemStack().isEmpty()) continue;
            for (var rightOption : inputs.get(1).getEmiStacks()) {
                long amount = inputs.get(1).getAmount();
                if (amount < 1 || amount > 64) continue;
                var right = rightOption.getItemStack().copyWithCount((int) amount);
                var left = leftOption.getItemStack().copyWithCount(1);
                // Repair displays may generate damage only in widgets; require an actual damaged target.
                if (left.isDamageableItem() && (ItemStack.isSameItem(left, right)
                        || left.getItem().isValidRepairItem(left, right))) left.setDamageValue(Math.max(1, left.getDamageValue()));
                var available = menu.getAnvilRecipeAvailability(left, right);
                if (available.requiredSlots() == 0) continue;
                int score = available.missing().missingSlots().size() * 4 + available.missing().craftableSlots().size();
                if (score < bestScore) {
                    best = new AnvilInputs(left, right, available);
                    bestScore = score;
                    if (score == 0) return best;
                }
            }
        }
        return best;
    }

    @Nullable private TianshuCraftingTermMenu.WorkRecipeAvailability availability(EmiRecipe recipe, M menu) {
        if (isAnvil(recipe)) {
            var inputs = anvilInputs(recipe, menu);
            return inputs == null ? null : inputs.available();
        }
        var holder = holder(recipe, menu);
        return holder == null ? null : menu.getWorkRecipeAvailability(holder.value());
    }

    @Override public boolean canCraft(EmiRecipe recipe, EmiCraftContext<M> context) {
        if (!(context.getScreen() instanceof TianshuCraftingTermScreen<?>)) return false;
        if (super.supportsRecipe(recipe)) return super.canCraft(recipe, context);
        var available = availability(recipe, context.getScreenHandler());
        return available != null && available.canTransfer();
    }

    @Override public boolean craft(EmiRecipe recipe, EmiCraftContext<M> context) {
        if (!canCraft(recipe, context)) return false;
        var menu = context.getScreenHandler();
        if (super.supportsRecipe(recipe)) {
            menu.prepareCraftingTransfer();
            return super.craft(recipe, context);
        }
        if (isAnvil(recipe)) {
            var inputs = anvilInputs(recipe, menu);
            if (inputs == null) return false;
            menu.fillAnvilRecipe(inputs.left(), inputs.right(), Screen.hasControlDown());
        } else {
            var holder = holder(recipe, menu);
            if (holder == null) return false;
            menu.fillWorkRecipe(holder.id().toString(), Screen.hasControlDown());
        }
        Minecraft.getInstance().setScreen(context.getScreen());
        return true;
    }

    @Override public List<ClientTooltipComponent> getTooltip(EmiRecipe recipe, EmiCraftContext<M> context) {
        if (super.supportsRecipe(recipe)) return super.getTooltip(recipe, context);
        var available = availability(recipe, context.getScreenHandler());
        if (available == null) return List.of();
        return TransferHelper.createCraftingTooltip(available.missing(), Screen.hasControlDown(), false).stream()
                .map(component -> ClientTooltipComponent.create(component.getVisualOrderText())).toList();
    }

    @Override public void render(EmiRecipe recipe, EmiCraftContext<M> context, List<Widget> widgets, GuiGraphics graphics) {
        if (super.supportsRecipe(recipe)) { super.render(recipe, context, widgets, graphics); return; }
        var available = availability(recipe, context.getScreenHandler());
        if (available == null) return;
        // The supported workstation layouts place their input widgets in native slot order.
        int index = 0;
        for (var widget : widgets) {
            if (!(widget instanceof SlotWidget slot) || slot.getRecipe() != null) continue;
            boolean missing = available.missing().missingSlots().contains(index);
            boolean craftable = available.missing().craftableSlots().contains(index++);
            if (!missing && !craftable) continue;
            var bounds = slot.getBounds();
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 400);
            graphics.fill(bounds.x() + 1, bounds.y() + 1, bounds.right() - 1, bounds.bottom() - 1,
                    missing ? TransferHelper.RED_SLOT_HIGHLIGHT_COLOR : TransferHelper.BLUE_SLOT_HIGHLIGHT_COLOR);
            graphics.pose().popPose();
        }
    }
}
