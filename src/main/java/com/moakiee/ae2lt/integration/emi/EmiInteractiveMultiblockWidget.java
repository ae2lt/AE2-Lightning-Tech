package com.moakiee.ae2lt.integration.emi;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;

import com.moakiee.ae2lt.integration.recipeviewer.multiblock.InteractiveMultiblockPreview;
import com.moakiee.ae2lt.integration.recipeviewer.multiblock.MultiblockStructureRecipe;

import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.Widget;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

/** EMI widget adapter for the shared interactive multiblock preview. */
final class EmiInteractiveMultiblockWidget extends Widget
        implements InteractiveMultiblockPreview.SlotDelegate {
    private static WeakReference<EmiInteractiveMultiblockWidget> activeWidget = new WeakReference<>(null);

    private final MultiblockStructureRecipe recipe;
    private final InteractiveMultiblockPreview preview;
    private final Bounds bounds;
    private final SlotWidget[] materialSlots;
    private final SlotWidget[] alternativeSlots =
            new SlotWidget[InteractiveMultiblockPreview.MAX_ALTERNATIVE_SLOTS];

    private SlotWidget selectedBlockSlot;
    private int lastMouseX;
    private int lastMouseY;
    private long lastTick = Long.MIN_VALUE;
    private Screen ownerScreen;
    private float screenOriginX;
    private float screenOriginY;

    EmiInteractiveMultiblockWidget(MultiblockStructureRecipe recipe, int width, int height) {
        this.recipe = recipe;
        this.bounds = new Bounds(0, 0, width, height);
        this.materialSlots = new SlotWidget[recipe.materials().size()];
        this.preview = new InteractiveMultiblockPreview(recipe, width, height, this);
    }

    @Override
    public Bounds getBounds() {
        return bounds;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        ownerScreen = Minecraft.getInstance().screen;
        Vector3f origin = guiGraphics.pose().last().pose()
                .transformPosition(new Vector3f(0.0F, 0.0F, 0.0F));
        screenOriginX = origin.x;
        screenOriginY = origin.y;
        if (activeWidget.get() != this) {
            activeWidget = new WeakReference<>(this);
        }
        long tick = Util.getMillis() / 50L;
        if (tick != lastTick) {
            preview.tick();
            lastTick = tick;
        }
        preview.drawWidget(guiGraphics, mouseX, mouseY);
    }

    @Override
    public List<ClientTooltipComponent> getTooltip(int mouseX, int mouseY) {
        SlotWidget slot = preview.getSlotUnderMouse(mouseX, mouseY)
                .map(this::slotFor)
                .orElse(null);
        if (slot != null) {
            List<ClientTooltipComponent> tooltip = slot.getTooltip(mouseX, mouseY);
            if (!tooltip.isEmpty()) {
                return tooltip;
            }
        }
        return toClientTooltip(preview.getTooltip(mouseX, mouseY));
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        SlotWidget slot = preview.getSlotUnderMouse(mouseX, mouseY)
                .map(this::slotFor)
                .orElse(null);
        if (slot != null && slot.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return preview.handleMouseClick(mouseX, mouseY, button, false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        SlotWidget slot = preview.getSlotUnderMouse(lastMouseX, lastMouseY)
                .map(this::slotFor)
                .orElse(null);
        return slot != null && slot.keyPressed(keyCode, scanCode, modifiers);
    }

    boolean handleMouseDragged(int mouseX, int mouseY, int button, double dragX, double dragY) {
        return preview.handleMouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    boolean handleMouseScrolled(int mouseX, int mouseY, double scrollX, double scrollY) {
        return preview.handleMouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    static boolean routeMouseDragged(
            Screen screen,
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY) {
        EmiInteractiveMultiblockWidget widget = activeWidget.get();
        if (widget == null || widget.ownerScreen != screen) {
            return false;
        }
        int localX = (int) Math.floor(mouseX - widget.screenOriginX);
        int localY = (int) Math.floor(mouseY - widget.screenOriginY);
        return widget.bounds.contains(localX, localY)
                && widget.handleMouseDragged(localX, localY, button, dragX, dragY);
    }

    static boolean routeMouseScrolled(
            Screen screen,
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY) {
        EmiInteractiveMultiblockWidget widget = activeWidget.get();
        if (widget == null || widget.ownerScreen != screen) {
            return false;
        }
        int localX = (int) Math.floor(mouseX - widget.screenOriginX);
        int localY = (int) Math.floor(mouseY - widget.screenOriginY);
        return widget.bounds.contains(localX, localY)
                && widget.handleMouseScrolled(localX, localY, scrollX, scrollY);
    }

    @Override
    public int materialSlotCount() {
        return materialSlots.length;
    }

    @Override
    public int alternativeSlotCount() {
        return alternativeSlots.length;
    }

    @Override
    public void drawMaterialSlot(GuiGraphics guiGraphics, int index, int x, int y) {
        var material = recipe.materials().get(index);
        SlotWidget slot = new SlotWidget(EmiStack.of(material.block()), x, y)
                .appendTooltip(Component.translatable("jei.ae2lt.multiblock.count", material.count()));
        if (!material.note().getString().isEmpty()) {
            slot.appendTooltip(material.note());
        }
        materialSlots[index] = slot;
        slot.render(guiGraphics, lastMouseX, lastMouseY, 0.0F);
    }

    @Override
    public void drawSelectedBlockSlot(GuiGraphics guiGraphics, Block block, int x, int y) {
        selectedBlockSlot = new SlotWidget(EmiStack.of(block), x, y);
        selectedBlockSlot.render(guiGraphics, lastMouseX, lastMouseY, 0.0F);
    }

    @Override
    public void drawAlternativeSlot(GuiGraphics guiGraphics, int index, Block block, int x, int y) {
        SlotWidget slot = new SlotWidget(EmiStack.of(block), x, y);
        alternativeSlots[index] = slot;
        slot.render(guiGraphics, lastMouseX, lastMouseY, 0.0F);
    }

    private SlotWidget slotFor(InteractiveMultiblockPreview.SlotReference reference) {
        return switch (reference.kind()) {
            case MATERIAL -> materialSlots[reference.index()];
            case SELECTED -> selectedBlockSlot;
            case ALTERNATIVE -> alternativeSlots[reference.index()];
        };
    }

    private static List<ClientTooltipComponent> toClientTooltip(List<Component> components) {
        List<ClientTooltipComponent> tooltip = new ArrayList<>(components.size());
        for (Component component : components) {
            tooltip.add(ClientTooltipComponent.create(component.getVisualOrderText()));
        }
        return tooltip;
    }
}
