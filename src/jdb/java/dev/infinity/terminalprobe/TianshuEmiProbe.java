package dev.infinity.terminalprobe;

import com.moakiee.ae2lt.menu.TianshuCraftingTermMenu;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.registry.EmiRecipeFiller;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.resources.ResourceLocation;

/** Real EMI registration/fill route in the disposable integration client. */
public final class TianshuEmiProbe {
    private static EmiRecipe selected;
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static String command(String command) {
        if (command.startsWith("recipe:")) {
            String kind = command.substring(7);
            if (kind.equals("anvil")) {
                selected = EmiApi.getRecipeManager().getRecipes(VanillaEmiRecipeCategories.ANVIL_REPAIRING).stream()
                        .filter(r -> r.getInputs().size() == 2
                                && r.getInputs().get(0).getEmiStacks().stream().anyMatch(s -> s.getItemStack().is(Items.IRON_PICKAXE))
                                && r.getInputs().get(1).getEmiStacks().stream().anyMatch(s -> s.getItemStack().is(Items.IRON_INGOT)))
                        .findFirst().orElseThrow();
            } else if (kind.equals("anvil4")) {
                selected = new dev.emi.emi.recipe.EmiAnvilRecipe(EmiStack.of(Items.IRON_PICKAXE),
                        EmiStack.of(new ItemStack(Items.IRON_INGOT, 4)), ResourceLocation.parse("probe:emi_anvil4"));
            } else {
                String id = switch (kind) {
                    case "smith" -> "allthemodium:smithing/allthemodium_sword_smithing";
                    case "stone" -> "minecraft:quartz_bricks_from_quartz_block_stonecutting";
                    case "crafting" -> "minecraft:crafting_table";
                    default -> kind;
                };
                selected = EmiApi.getRecipeManager().getRecipe(ResourceLocation.parse(id));
                if (selected == null) throw new IllegalStateException("EMI recipe not loaded " + id);
            }
            EmiApi.displayRecipe(selected);
            return "EMI showing " + selected.getId() + "; recipeClass=" + selected.getClass().getName();
        }
        var screen = (AbstractContainerScreen<TianshuCraftingTermMenu>) EmiApi.getHandledScreen();
        if (screen == null) throw new IllegalStateException("No EMI handled screen");
        var handler = EmiRecipeFiller.getFirstValidHandler(selected, screen);
        if (handler == null) throw new IllegalStateException("No actual EMI handler");
        var context = new EmiCraftContext<>(screen, handler.getInventory(screen), EmiCraftContext.Type.FILL_BUTTON);
        String report = "EMI handler=" + handler.getClass().getName() + "; canCraft=" + handler.canCraft(selected, context);
        if (command.equals("feedback")) report += "; tooltips=" + handler.getTooltip(selected, context).size();
        if (command.equals("transfer")) report += "; filled=" + EmiRecipeFiller.performFill(selected, screen,
                EmiCraftContext.Type.FILL_BUTTON, EmiCraftContext.Destination.NONE, 1);
        return report;
    }
}
