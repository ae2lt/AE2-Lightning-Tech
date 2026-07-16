package com.moakiee.ae2lt.integration.jei.category;

import java.util.ArrayList;
import java.util.List;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.integration.jei.multiblock.InteractiveMultiblockWidget;
import com.moakiee.ae2lt.integration.jei.multiblock.MultiblockStructureRecipe;
import com.moakiee.ae2lt.registry.ModBlocks;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawablesView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** JEI category that hosts interactive construction previews for large multiblocks. */
public final class MultiblockStructureCategory implements IRecipeCategory<MultiblockStructureRecipe> {
    public static final int WIDTH = 300;
    public static final int HEIGHT = 184;

    private static final int PANEL_WIDTH = 105;
    private static final int PANEL_X = WIDTH - PANEL_WIDTH - 2;
    private static final int PANEL_CONTENT_TOP = 48;
    private static final int MATERIAL_ROW_STEP = 26;
    private static final int MATERIAL_SLOT_X = PANEL_X + 4;
    private static final int MATERIAL_SLOT_Y = PANEL_CONTENT_TOP + 4;
    private static final int SELECTED_SLOT_X = PANEL_X + 4;
    private static final int SELECTED_SLOT_Y = PANEL_CONTENT_TOP + 2;
    private static final int ALTERNATIVE_SLOT_X = PANEL_X + 4;
    private static final int ALTERNATIVE_SLOT_Y = PANEL_CONTENT_TOP + 63;
    private static final int ALTERNATIVE_COLUMN_STEP = 23;
    private static final int ALTERNATIVE_ROW_STEP = 21;
    private static final int ALTERNATIVE_COLUMNS = 4;

    public static final RecipeType<MultiblockStructureRecipe> TYPE = RecipeType.create(
            AE2LightningTech.MODID,
            "multiblock_structure",
            MultiblockStructureRecipe.class);

    private final IDrawable icon;

    public MultiblockStructureCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(
                new ItemStack(ModBlocks.MATTER_WARPING_MATRIX_CONTROLLER.get()));
    }

    @Override
    public RecipeType<MultiblockStructureRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.ae2lt.multiblock.title");
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(
            IRecipeLayoutBuilder builder,
            MultiblockStructureRecipe recipe,
            IFocusGroup focuses) {
        List<MultiblockStructureRecipe.MaterialEntry> materials = recipe.materials();
        for (int i = 0; i < materials.size(); i++) {
            var material = materials.get(i);
            ItemStack displayStack = new ItemStack(material.block());
            ItemStack transferStack = displayStack.copy();
            transferStack.setCount(material.count());
            int y = MATERIAL_SLOT_Y + i * MATERIAL_ROW_STEP;

            builder.addSlot(RecipeIngredientRole.CATALYST, MATERIAL_SLOT_X, y)
                    .setSlotName(InteractiveMultiblockWidget.MATERIAL_SLOT_PREFIX + i)
                    .setStandardSlotBackground()
                    .addItemStack(displayStack)
                    .addRichTooltipCallback((slot, tooltip) -> {
                        tooltip.add(Component.translatable(
                                "jei.ae2lt.multiblock.count",
                                material.count()));
                        if (!material.note().getString().isEmpty()) {
                            tooltip.add(material.note());
                        }
                    });
            builder.addSlot(RecipeIngredientRole.INPUT)
                    .setSlotName(InteractiveMultiblockWidget.TRANSFER_SLOT_PREFIX + i)
                    .addItemStack(transferStack);
        }

        builder.addSlot(RecipeIngredientRole.CATALYST, SELECTED_SLOT_X, SELECTED_SLOT_Y)
                .setSlotName(InteractiveMultiblockWidget.SELECTED_BLOCK_SLOT)
                .setStandardSlotBackground()
                .addItemStacks(recipe.focusStacks());

        for (int i = 0; i < InteractiveMultiblockWidget.MAX_ALTERNATIVE_SLOTS; i++) {
            int x = ALTERNATIVE_SLOT_X + i % ALTERNATIVE_COLUMNS * ALTERNATIVE_COLUMN_STEP;
            int y = ALTERNATIVE_SLOT_Y + i / ALTERNATIVE_COLUMNS * ALTERNATIVE_ROW_STEP;
            builder.addSlot(RecipeIngredientRole.CATALYST, x, y)
                    .setSlotName(InteractiveMultiblockWidget.ALTERNATIVE_SLOT_PREFIX + i)
                    .setStandardSlotBackground()
                    .addItemStacks(recipe.focusStacks());
        }

        // Index 0 is the transfer button; JEI stacks the bookmark button above it.
        // Anchor both outside the lower-right corner so neither covers the page.
        builder.moveRecipeTransferButton(WIDTH + 4, HEIGHT - 14);
    }

    @Override
    public void createRecipeExtras(
            IRecipeExtrasBuilder builder,
            MultiblockStructureRecipe recipe,
            IFocusGroup focuses) {
        IRecipeSlotDrawablesView slots = builder.getRecipeSlots();
        List<IRecipeSlotDrawable> materialSlots = new ArrayList<>(recipe.materials().size());
        for (int i = 0; i < recipe.materials().size(); i++) {
            materialSlots.add(requireSlot(slots, InteractiveMultiblockWidget.MATERIAL_SLOT_PREFIX + i));
        }
        List<IRecipeSlotDrawable> transferSlots = new ArrayList<>(recipe.materials().size());
        for (int i = 0; i < recipe.materials().size(); i++) {
            transferSlots.add(requireSlot(slots, InteractiveMultiblockWidget.TRANSFER_SLOT_PREFIX + i));
        }
        IRecipeSlotDrawable selectedBlockSlot = requireSlot(
                slots,
                InteractiveMultiblockWidget.SELECTED_BLOCK_SLOT);
        List<IRecipeSlotDrawable> alternativeSlots = new ArrayList<>(
                InteractiveMultiblockWidget.MAX_ALTERNATIVE_SLOTS);
        for (int i = 0; i < InteractiveMultiblockWidget.MAX_ALTERNATIVE_SLOTS; i++) {
            alternativeSlots.add(requireSlot(
                    slots,
                    InteractiveMultiblockWidget.ALTERNATIVE_SLOT_PREFIX + i));
        }

        var widget = new InteractiveMultiblockWidget(
                recipe,
                WIDTH,
                HEIGHT,
                materialSlots,
                selectedBlockSlot,
                alternativeSlots);
        List<IRecipeSlotDrawable> widgetSlots = new ArrayList<>(materialSlots);
        widgetSlots.addAll(transferSlots);
        widgetSlots.add(selectedBlockSlot);
        widgetSlots.addAll(alternativeSlots);
        builder.addSlottedWidget(widget, widgetSlots);
        builder.addInputHandler(widget);
    }

    private static IRecipeSlotDrawable requireSlot(IRecipeSlotDrawablesView slots, String name) {
        return slots.findSlotByName(name)
                .orElseThrow(() -> new IllegalStateException("Missing JEI slot: " + name));
    }

    @Override
    public ResourceLocation getRegistryName(MultiblockStructureRecipe recipe) {
        return recipe.id();
    }
}
