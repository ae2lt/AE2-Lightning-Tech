package com.moakiee.ae2lt.integration.jei.multiblock;

import java.util.List;
import java.util.Optional;

import com.mojang.blaze3d.platform.InputConstants;

import com.moakiee.ae2lt.integration.recipeviewer.multiblock.InteractiveMultiblockPreview;
import com.moakiee.ae2lt.integration.recipeviewer.multiblock.MultiblockStructureRecipe;

import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.gui.widgets.ISlottedRecipeWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/** JEI adapter for the shared interactive multiblock preview. */
public final class InteractiveMultiblockWidget
        implements ISlottedRecipeWidget, IJeiInputHandler, InteractiveMultiblockPreview.SlotDelegate {
    public static final String MATERIAL_SLOT_PREFIX = "multiblock_material_";
    public static final String TRANSFER_SLOT_PREFIX = "multiblock_transfer_";
    public static final String SELECTED_BLOCK_SLOT = "multiblock_selected_block";
    public static final String ALTERNATIVE_SLOT_PREFIX = "multiblock_alternative_";
    public static final int MAX_ALTERNATIVE_SLOTS = InteractiveMultiblockPreview.MAX_ALTERNATIVE_SLOTS;

    private static final ScreenPosition POSITION = new ScreenPosition(0, 0);

    private final InteractiveMultiblockPreview preview;
    private final ScreenRectangle area;
    private final List<IRecipeSlotDrawable> materialSlots;
    private final IRecipeSlotDrawable selectedBlockSlot;
    private final List<IRecipeSlotDrawable> alternativeSlots;

    public InteractiveMultiblockWidget(
            MultiblockStructureRecipe recipe,
            int width,
            int height,
            List<IRecipeSlotDrawable> materialSlots,
            IRecipeSlotDrawable selectedBlockSlot,
            List<IRecipeSlotDrawable> alternativeSlots) {
        this.materialSlots = List.copyOf(materialSlots);
        this.selectedBlockSlot = selectedBlockSlot;
        this.alternativeSlots = List.copyOf(alternativeSlots);
        this.preview = new InteractiveMultiblockPreview(recipe, width, height, this);
        this.area = new ScreenRectangle(0, 0, width, height);
    }

    @Override
    public ScreenPosition getPosition() {
        return POSITION;
    }

    @Override
    public ScreenRectangle getArea() {
        return area;
    }

    @Override
    public void drawWidget(GuiGraphics guiGraphics, double mouseX, double mouseY) {
        preview.drawWidget(guiGraphics, mouseX, mouseY);
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, double mouseX, double mouseY) {
        tooltip.addAll(preview.getTooltip(mouseX, mouseY));
    }

    @Override
    public Optional<RecipeSlotUnderMouse> getSlotUnderMouse(double mouseX, double mouseY) {
        return preview.getSlotUnderMouse(mouseX, mouseY)
                .map(this::slotFor)
                .map(slot -> new RecipeSlotUnderMouse(slot, POSITION));
    }

    @Override
    public boolean handleInput(double mouseX, double mouseY, IJeiUserInput input) {
        InputConstants.Key key = input.getKey();
        if (key.getType() != InputConstants.Type.MOUSE) {
            return false;
        }
        return preview.handleMouseClick(mouseX, mouseY, key.getValue(), input.isSimulate());
    }

    @Override
    public boolean handleMouseDragged(
            double mouseX,
            double mouseY,
            InputConstants.Key key,
            double dragX,
            double dragY) {
        if (key.getType() != InputConstants.Type.MOUSE) {
            return false;
        }
        return preview.handleMouseDragged(mouseX, mouseY, key.getValue(), dragX, dragY);
    }

    @Override
    public boolean handleMouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return preview.handleMouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void tick() {
        preview.tick();
    }

    @Override
    public int materialSlotCount() {
        return materialSlots.size();
    }

    @Override
    public int alternativeSlotCount() {
        return alternativeSlots.size();
    }

    @Override
    public void drawMaterialSlot(GuiGraphics guiGraphics, int index, int x, int y) {
        IRecipeSlotDrawable slot = materialSlots.get(index);
        slot.setPosition(x, y);
        slot.draw(guiGraphics);
    }

    @Override
    public void drawSelectedBlockSlot(GuiGraphics guiGraphics, Block block, int x, int y) {
        setSlotStack(selectedBlockSlot, block, x, y);
        selectedBlockSlot.draw(guiGraphics);
    }

    @Override
    public void drawAlternativeSlot(GuiGraphics guiGraphics, int index, Block block, int x, int y) {
        IRecipeSlotDrawable slot = alternativeSlots.get(index);
        setSlotStack(slot, block, x, y);
        slot.draw(guiGraphics);
    }

    private IRecipeSlotDrawable slotFor(InteractiveMultiblockPreview.SlotReference reference) {
        return switch (reference.kind()) {
            case MATERIAL -> materialSlots.get(reference.index());
            case SELECTED -> selectedBlockSlot;
            case ALTERNATIVE -> alternativeSlots.get(reference.index());
        };
    }

    private static void setSlotStack(IRecipeSlotDrawable slot, Block block, int x, int y) {
        slot.setPosition(x, y);
        slot.clearDisplayOverrides();
        slot.createDisplayOverrides().addItemStack(new ItemStack(block));
    }
}
