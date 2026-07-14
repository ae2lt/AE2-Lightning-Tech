package com.moakiee.ae2lt.integration.jei.multiblock;

import java.util.List;
import java.util.Optional;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.gui.widgets.ISlottedRecipeWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.neoforged.neoforge.client.model.data.ModelData;

import appeng.client.render.overlay.OverlayRenderType;

/** Interactive, orthographic 3D multiblock viewer embedded in a JEI recipe page. */
public final class InteractiveMultiblockWidget implements ISlottedRecipeWidget, IJeiInputHandler {
    public static final String MATERIAL_SLOT_PREFIX = "multiblock_material_";
    public static final String TRANSFER_SLOT_PREFIX = "multiblock_transfer_";
    public static final String SELECTED_BLOCK_SLOT = "multiblock_selected_block";
    public static final String ALTERNATIVE_SLOT_PREFIX = "multiblock_alternative_";
    public static final int MAX_ALTERNATIVE_SLOTS = 7;

    private static final int NAME_Y = 1;
    private static final int TOOLBAR_Y = 14;
    private static final int TOOLBAR_HEIGHT = 14;
    private static final int VIEW_X = 2;
    private static final int VIEW_Y = 31;
    private static final int PANEL_WIDTH = 105;
    private static final int FOOTER_HEIGHT = 17;
    private static final int PANEL_TAB_HEIGHT = 15;
    private static final int MATERIAL_ROW_HEIGHT = 26;

    private static final float DEFAULT_YAW = 225.0F;
    private static final float DEFAULT_PITCH = 30.0F;
    private static final float MIN_ZOOM = 0.35F;
    private static final float MAX_ZOOM = 3.5F;
    private static final float AUTO_ROTATION_PER_TICK = 0.35F;
    private static final float MODEL_Z = 80.0F;
    private static final float DEPTH_SCALE_FACTOR = 0.1F;

    private static final int PANEL_BACKGROUND = 0xFF181818;
    private static final int VIEW_BACKGROUND = 0xFF101010;
    private static final int BORDER_COLOR = 0xFF626262;
    private static final int ACTIVE_COLOR = 0xFF444444;
    private static final int HOVER_COLOR = 0xFF353535;
    private static final int BUTTON_COLOR = 0xFF262626;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int MUTED_TEXT_COLOR = 0xFFD0D0D0;
    private static final int FOOTER_TEXT_COLOR = 0xFF000000;
    private static final int SELECTED_GLOW_ALPHA = 132;
    private static final int HOVERED_GLOW_ALPHA = 190;

    private final MultiblockStructureRecipe recipe;
    private final int width;
    private final int height;
    private final int panelX;
    private final int panelY;
    private final int panelHeight;
    private final int viewWidth;
    private final int viewHeight;
    private final float baseScale;
    private final ScreenRectangle area;
    private final List<IRecipeSlotDrawable> materialSlots;
    private final IRecipeSlotDrawable selectedBlockSlot;
    private final List<IRecipeSlotDrawable> alternativeSlots;

    private final UiRect resetButton = new UiRect(2, TOOLBAR_Y, 34, TOOLBAR_HEIGHT);
    private final UiRect autoButton = new UiRect(38, TOOLBAR_Y, 38, TOOLBAR_HEIGHT);
    private final UiRect shellButton = new UiRect(78, TOOLBAR_Y, 40, TOOLBAR_HEIGHT);
    private final UiRect layerModeButton = new UiRect(120, TOOLBAR_Y, 52, TOOLBAR_HEIGHT);
    private final UiRect layerDownButton = new UiRect(174, TOOLBAR_Y, 14, TOOLBAR_HEIGHT);
    private final UiRect layerUpButton = new UiRect(235, TOOLBAR_Y, 14, TOOLBAR_HEIGHT);

    private float yaw = DEFAULT_YAW;
    private float pitch = DEFAULT_PITCH;
    private float zoom = 1.0F;
    private float panX;
    private float panY;
    private boolean autoRotate = true;
    private boolean hideShell;
    private LayerMode layerMode = LayerMode.FULL;
    private int layer;
    private int materialScroll;
    private PanelTab panelTab = PanelTab.MATERIALS;
    private MultiblockStructureRecipe.Cell selectedCell;
    private MultiblockStructureRecipe.Cell hoveredCell;
    private MultiblockStructureRecipe.Cell slotsForCell;

    public InteractiveMultiblockWidget(
            MultiblockStructureRecipe recipe,
            int width,
            int height,
            List<IRecipeSlotDrawable> materialSlots,
            IRecipeSlotDrawable selectedBlockSlot,
            List<IRecipeSlotDrawable> alternativeSlots) {
        this.recipe = recipe;
        this.width = width;
        this.height = height;
        this.panelX = width - PANEL_WIDTH - 2;
        this.panelY = VIEW_Y;
        this.panelHeight = height - VIEW_Y - FOOTER_HEIGHT;
        this.viewWidth = panelX - VIEW_X - 4;
        this.viewHeight = panelHeight;
        this.layer = (recipe.sizeY() - 1) / 2;
        this.area = new ScreenRectangle(0, 0, width, height);
        this.materialSlots = List.copyOf(materialSlots);
        this.selectedBlockSlot = selectedBlockSlot;
        this.alternativeSlots = List.copyOf(alternativeSlots);

        float horizontal = (float) Math.hypot(recipe.sizeX(), recipe.sizeZ());
        float projectedHeight = recipe.sizeY() * 0.866F + horizontal * 0.5F;
        float widthScale = viewWidth * 0.90F / Math.max(1.0F, horizontal);
        float heightScale = viewHeight * 0.90F / Math.max(1.0F, projectedHeight);
        this.baseScale = Math.min(widthScale, heightScale);
    }

    @Override
    public ScreenPosition getPosition() {
        return new ScreenPosition(0, 0);
    }

    @Override
    public ScreenRectangle getArea() {
        return area;
    }

    @Override
    public void drawWidget(GuiGraphics guiGraphics, double mouseX, double mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;

        hoveredCell = inside(VIEW_X, VIEW_Y, viewWidth, viewHeight, mouseX, mouseY)
                ? pickCell(mouseX, mouseY)
                : null;

        drawHeader(guiGraphics, font);
        drawToolbar(guiGraphics, font, mouseX, mouseY);
        drawViewport(guiGraphics);
        drawPanel(guiGraphics, font, mouseX, mouseY);
        drawFooter(guiGraphics, font);
    }

    private void drawHeader(GuiGraphics guiGraphics, Font font) {
        drawTrimmed(guiGraphics, font, recipe.title(), 2, NAME_Y, width - 70, TEXT_COLOR);
        String dimensions = recipe.sizeX() + "x" + recipe.sizeY() + "x" + recipe.sizeZ();
        guiGraphics.drawString(font, dimensions, width - font.width(dimensions) - 2, NAME_Y, MUTED_TEXT_COLOR, false);
    }

    private void drawToolbar(GuiGraphics guiGraphics, Font font, double mouseX, double mouseY) {
        drawButton(guiGraphics, font, resetButton, Component.translatable("jei.ae2lt.multiblock.reset"),
                false, mouseX, mouseY);
        drawButton(guiGraphics, font, autoButton,
                Component.translatable(autoRotate
                        ? "jei.ae2lt.multiblock.auto.on"
                        : "jei.ae2lt.multiblock.auto.off"),
                autoRotate, mouseX, mouseY);
        drawButton(guiGraphics, font, shellButton,
                Component.translatable(hideShell
                        ? "jei.ae2lt.multiblock.shell.hidden"
                        : "jei.ae2lt.multiblock.shell.visible"),
                hideShell, mouseX, mouseY);
        drawButton(guiGraphics, font, layerModeButton, layerMode.label(),
                layerMode != LayerMode.FULL, mouseX, mouseY);
        drawButton(guiGraphics, font, layerDownButton, Component.literal("-"),
                false, mouseX, mouseY);
        drawButton(guiGraphics, font, layerUpButton, Component.literal("+"),
                false, mouseX, mouseY);

        UiRect layerLabel = layerLabelRect();
        guiGraphics.fill(layerLabel.x(), layerLabel.y(), layerLabel.right(), layerLabel.bottom(), PANEL_BACKGROUND);
        guiGraphics.renderOutline(layerLabel.x(), layerLabel.y(), layerLabel.width(), layerLabel.height(), BORDER_COLOR);
        Component text = Component.translatable(
                "jei.ae2lt.multiblock.layer",
                layer + 1,
                recipe.sizeY());
        drawCenteredTrimmed(guiGraphics, font, text, layerLabel, MUTED_TEXT_COLOR);
    }

    private void drawViewport(GuiGraphics guiGraphics) {
        guiGraphics.fill(VIEW_X, VIEW_Y, VIEW_X + viewWidth, VIEW_Y + viewHeight, VIEW_BACKGROUND);
        guiGraphics.renderOutline(VIEW_X, VIEW_Y, viewWidth, viewHeight, BORDER_COLOR);
        enableLocalScissor(
                guiGraphics,
                VIEW_X + 1,
                VIEW_Y + 1,
                VIEW_X + viewWidth - 1,
                VIEW_Y + viewHeight - 1);

        Minecraft client = Minecraft.getInstance();
        var bufferSource = client.renderBuffers().bufferSource();
        var blockRenderer = client.getBlockRenderer();
        PoseStack pose = guiGraphics.pose();
        float scale = renderScale();

        pose.pushPose();
        pose.translate(viewCenterX() + panX, viewCenterY() + panY, MODEL_Z);
        pose.scale(scale, -scale, scale * DEPTH_SCALE_FACTOR);
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.translate(-recipe.sizeX() / 2.0F, -recipe.sizeY() / 2.0F, -recipe.sizeZ() / 2.0F);

        for (var cell : recipe.cells()) {
            if (!isVisible(cell) || cell.state().getRenderShape() == RenderShape.ENTITYBLOCK_ANIMATED) {
                continue;
            }
            pose.pushPose();
            pose.translate(cell.localPos().getX(), cell.localPos().getY(), cell.localPos().getZ());
            blockRenderer.renderSingleBlock(
                    cell.state(),
                    pose,
                    bufferSource,
                    LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY,
                    ModelData.EMPTY,
                    null);
            pose.popPose();
        }
        bufferSource.endBatch();

        if (selectedCell != null && selectedCell != hoveredCell && isVisible(selectedCell)) {
            renderGlowCube(pose, bufferSource, selectedCell, SELECTED_GLOW_ALPHA);
        }
        if (hoveredCell != null && isVisible(hoveredCell)) {
            renderGlowCube(pose, bufferSource, hoveredCell, HOVERED_GLOW_ALPHA);
        }
        pose.popPose();
        bufferSource.endBatch(OverlayRenderType.getBlockHilightFace());
        Lighting.setupFor3DItems();
        guiGraphics.disableScissor();
    }

    private static void renderGlowCube(
            PoseStack pose,
            MultiBufferSource bufferSource,
            MultiblockStructureRecipe.Cell cell,
            int alpha) {
        VertexConsumer consumer = bufferSource.getBuffer(OverlayRenderType.getBlockHilightFace());
        pose.pushPose();
        pose.translate(
                cell.localPos().getX(),
                cell.localPos().getY(),
                cell.localPos().getZ());

        Matrix4f matrix = pose.last().pose();
        float low = -0.025F;
        float high = 1.025F;

        glowQuad(consumer, matrix, alpha,
                low, low, low, high, low, low, high, low, high, low, low, high, 0.0F, -1.0F, 0.0F);
        glowQuad(consumer, matrix, alpha,
                low, high, high, high, high, high, high, high, low, low, high, low, 0.0F, 1.0F, 0.0F);
        glowQuad(consumer, matrix, alpha,
                low, low, low, low, high, low, high, high, low, high, low, low, 0.0F, 0.0F, -1.0F);
        glowQuad(consumer, matrix, alpha,
                high, low, high, high, high, high, low, high, high, low, low, high, 0.0F, 0.0F, 1.0F);
        glowQuad(consumer, matrix, alpha,
                low, low, high, low, high, high, low, high, low, low, low, low, -1.0F, 0.0F, 0.0F);
        glowQuad(consumer, matrix, alpha,
                high, low, low, high, high, low, high, high, high, high, low, high, 1.0F, 0.0F, 0.0F);
        pose.popPose();
    }

    private static void glowQuad(
            VertexConsumer consumer,
            Matrix4f matrix,
            int alpha,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float x4, float y4, float z4,
            float normalX, float normalY, float normalZ) {
        consumer.addVertex(matrix, x1, y1, z1).setColor(255, 220, 0, alpha)
                .setNormal(normalX, normalY, normalZ);
        consumer.addVertex(matrix, x2, y2, z2).setColor(255, 220, 0, alpha)
                .setNormal(normalX, normalY, normalZ);
        consumer.addVertex(matrix, x3, y3, z3).setColor(255, 220, 0, alpha)
                .setNormal(normalX, normalY, normalZ);
        consumer.addVertex(matrix, x4, y4, z4).setColor(255, 220, 0, alpha)
                .setNormal(normalX, normalY, normalZ);
    }

    private void drawPanel(GuiGraphics guiGraphics, Font font, double mouseX, double mouseY) {
        guiGraphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + panelHeight, PANEL_BACKGROUND);
        guiGraphics.renderOutline(panelX, panelY, PANEL_WIDTH, panelHeight, BORDER_COLOR);

        UiRect materialsTab = materialsTabRect();
        UiRect detailsTab = detailsTabRect();
        drawTab(guiGraphics, font, materialsTab,
                Component.translatable("jei.ae2lt.multiblock.materials"),
                panelTab == PanelTab.MATERIALS, mouseX, mouseY);
        drawTab(guiGraphics, font, detailsTab,
                Component.translatable("jei.ae2lt.multiblock.details"),
                panelTab == PanelTab.DETAILS, mouseX, mouseY);

        if (panelTab == PanelTab.MATERIALS) {
            drawMaterials(guiGraphics, font, mouseX, mouseY);
        } else {
            drawDetails(guiGraphics, font);
        }
    }

    private void drawMaterials(GuiGraphics guiGraphics, Font font, double mouseX, double mouseY) {
        int contentTop = panelContentTop();
        int contentBottom = panelContentBottom();
        enableLocalScissor(guiGraphics, panelX + 1, contentTop, panelX + PANEL_WIDTH - 1, contentBottom);

        List<MultiblockStructureRecipe.MaterialEntry> materials = recipe.materials();
        int count = Math.min(materials.size(), materialSlots.size());
        for (int i = 0; i < count; i++) {
            int rowY = materialRowY(i);
            if (rowY + MATERIAL_ROW_HEIGHT <= contentTop || rowY >= contentBottom) {
                continue;
            }

            IRecipeSlotDrawable slot = materialSlots.get(i);
            slot.setPosition(panelX + 4, rowY + 4);
            boolean hovered = inside(
                    panelX + 1,
                    rowY,
                    PANEL_WIDTH - 2,
                    MATERIAL_ROW_HEIGHT,
                    mouseX,
                    mouseY);
            guiGraphics.fill(
                    panelX + 1,
                    rowY,
                    panelX + PANEL_WIDTH - 1,
                    rowY + MATERIAL_ROW_HEIGHT,
                    hovered ? HOVER_COLOR : (i % 2 == 0 ? 0xFF202020 : 0xFF1C1C1C));

            slot.draw(guiGraphics);
            var material = materials.get(i);
            int textX = panelX + 27;
            int textWidth = PANEL_WIDTH - 34;
            drawTrimmed(guiGraphics, font, material.block().getName(), textX, rowY + 3,
                    textWidth, TEXT_COLOR);
            drawTrimmed(
                    guiGraphics,
                    font,
                    Component.translatable("jei.ae2lt.multiblock.count", material.count()),
                    textX,
                    rowY + 14,
                    textWidth,
                    MUTED_TEXT_COLOR);
        }
        guiGraphics.disableScissor();
        drawMaterialScrollbar(guiGraphics);
    }

    private void drawMaterialScrollbar(GuiGraphics guiGraphics) {
        int maxScroll = maxMaterialScroll();
        if (maxScroll <= 0) {
            return;
        }
        int trackTop = panelContentTop() + 1;
        int trackHeight = panelContentHeight() - 2;
        int trackX = panelX + PANEL_WIDTH - 4;
        int contentHeight = recipe.materials().size() * MATERIAL_ROW_HEIGHT;
        int thumbHeight = Math.max(12, trackHeight * panelContentHeight() / contentHeight);
        int thumbTravel = Math.max(0, trackHeight - thumbHeight);
        int thumbY = trackTop + (int) Math.round((double) materialScroll / maxScroll * thumbTravel);
        guiGraphics.fill(trackX, trackTop, trackX + 2, trackTop + trackHeight, 0xFF303030);
        guiGraphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, 0xFFB0B0B0);
    }

    private void drawDetails(GuiGraphics guiGraphics, Font font) {
        int contentTop = panelContentTop();
        if (selectedCell == null) {
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(
                    Component.translatable("jei.ae2lt.multiblock.select_hint"),
                    PANEL_WIDTH - 10);
            int y = contentTop + 5;
            for (var line : lines) {
                guiGraphics.drawString(font, line, panelX + 5, y, MUTED_TEXT_COLOR, false);
                y += font.lineHeight + 2;
            }
            return;
        }

        updateDetailSlotOverrides();
        Block selectedBlock = selectedCell.state().getBlock();
        selectedBlockSlot.draw(guiGraphics);
        drawTrimmed(guiGraphics, font, selectedBlock.getName(), panelX + 25, contentTop + 3,
                PANEL_WIDTH - 27, TEXT_COLOR);
        drawTrimmed(guiGraphics, font,
                Component.translatable("jei.ae2lt.multiblock.role_label", selectedCell.role()),
                panelX + 4, contentTop + 24, PANEL_WIDTH - 8, MUTED_TEXT_COLOR);
        drawTrimmed(guiGraphics, font,
                Component.translatable(
                        "jei.ae2lt.multiblock.position",
                        selectedCell.localPos().getX(),
                        selectedCell.localPos().getY(),
                        selectedCell.localPos().getZ()),
                panelX + 4, contentTop + 35, PANEL_WIDTH - 8, MUTED_TEXT_COLOR);
        guiGraphics.drawString(
                font,
                Component.translatable("jei.ae2lt.multiblock.replacements"),
                panelX + 4,
                contentTop + 48,
                TEXT_COLOR,
                false);

        List<Block> alternatives = visibleAlternatives();
        boolean allowsAir = selectedCell.alternatives().contains(Blocks.AIR);
        if (alternatives.size() == 1 && !allowsAir) {
            guiGraphics.drawString(
                    font,
                    Component.translatable("jei.ae2lt.multiblock.fixed"),
                    panelX + 4,
                    contentTop + 62,
                    MUTED_TEXT_COLOR,
                    false);
        } else {
            for (int i = 0; i < Math.min(alternatives.size(), alternativeSlots.size()); i++) {
                alternativeSlots.get(i).draw(guiGraphics);
            }
            if (allowsAir) {
                UiRect airRect = airAlternativeRect();
                guiGraphics.fill(airRect.x(), airRect.y(), airRect.right(), airRect.bottom(), BUTTON_COLOR);
                guiGraphics.renderOutline(
                        airRect.x(),
                        airRect.y(),
                        airRect.width(),
                        airRect.height(),
                        BORDER_COLOR);
                String airMarker = "X";
                guiGraphics.drawString(
                        font,
                        airMarker,
                        airRect.x() + (airRect.width() - font.width(airMarker)) / 2,
                        airRect.y() + 5,
                        MUTED_TEXT_COLOR,
                        false);
            }
        }

        if (!selectedCell.rules().isEmpty()) {
            int ruleY = panelContentBottom() - font.lineHeight - 2;
            drawTrimmed(guiGraphics, font, selectedCell.rules().getFirst(), panelX + 4, ruleY,
                    PANEL_WIDTH - 8, MUTED_TEXT_COLOR);
        }
    }

    private void drawFooter(GuiGraphics guiGraphics, Font font) {
        Component controls = Component.translatable("jei.ae2lt.multiblock.controls");
        drawCenteredTrimmed(
                guiGraphics,
                font,
                controls,
                new UiRect(2, height - FOOTER_HEIGHT + 3, width - 4, font.lineHeight),
                FOOTER_TEXT_COLOR);
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, double mouseX, double mouseY) {
        Component toolbarTooltip = toolbarTooltip(mouseX, mouseY);
        if (toolbarTooltip != null) {
            tooltip.add(toolbarTooltip);
            return;
        }

        if (inside(VIEW_X, VIEW_Y, viewWidth, viewHeight, mouseX, mouseY)) {
            MultiblockStructureRecipe.Cell hovered = pickCell(mouseX, mouseY);
            if (hovered != null) {
                tooltip.add(hovered.state().getBlock().getName());
                tooltip.add(Component.translatable("jei.ae2lt.multiblock.role_label", hovered.role()));
                tooltip.add(Component.translatable("jei.ae2lt.multiblock.click_for_details"));
            }
            return;
        }

        if (!inside(panelX, panelY, PANEL_WIDTH, panelHeight, mouseX, mouseY)) {
            return;
        }
        if (panelTab == PanelTab.DETAILS && selectedCell != null) {
            UiRect airRect = airAlternativeRect();
            if (airRect != null && airRect.contains(mouseX, mouseY)) {
                tooltip.add(Component.translatable("jei.ae2lt.multiblock.air"));
            } else if (mouseY >= panelContentBottom() - 14) {
                tooltip.addAll(selectedCell.rules());
            }
        }
    }

    @Override
    public Optional<RecipeSlotUnderMouse> getSlotUnderMouse(double mouseX, double mouseY) {
        if (panelTab == PanelTab.MATERIALS) {
            if (!inside(panelX, panelContentTop(), PANEL_WIDTH, panelContentHeight(), mouseX, mouseY)) {
                return Optional.empty();
            }
            for (int i = 0; i < materialSlots.size(); i++) {
                int rowY = materialRowY(i);
                if (rowY + MATERIAL_ROW_HEIGHT <= panelContentTop() || rowY >= panelContentBottom()) {
                    continue;
                }
                IRecipeSlotDrawable slot = materialSlots.get(i);
                slot.setPosition(panelX + 4, rowY + 4);
                if (slot.isMouseOver(mouseX, mouseY)) {
                    return Optional.of(new RecipeSlotUnderMouse(slot, getPosition()));
                }
            }
            return Optional.empty();
        }
        if (selectedCell == null) {
            return Optional.empty();
        }

        updateDetailSlotOverrides();
        if (selectedBlockSlot.isMouseOver(mouseX, mouseY)) {
            return Optional.of(new RecipeSlotUnderMouse(selectedBlockSlot, getPosition()));
        }
        int visibleCount = Math.min(visibleAlternatives().size(), alternativeSlots.size());
        return slotUnderMouse(alternativeSlots.subList(0, visibleCount), mouseX, mouseY);
    }

    private Optional<RecipeSlotUnderMouse> slotUnderMouse(
            List<IRecipeSlotDrawable> slots,
            double mouseX,
            double mouseY) {
        return slots.stream()
                .filter(slot -> slot.isMouseOver(mouseX, mouseY))
                .findFirst()
                .map(slot -> new RecipeSlotUnderMouse(slot, getPosition()));
    }

    @Override
    public boolean handleInput(double mouseX, double mouseY, IJeiUserInput input) {
        InputConstants.Key key = input.getKey();
        if (key.getType() != InputConstants.Type.MOUSE) {
            return false;
        }
        int button = key.getValue();
        if (button != 0 && button != 2) {
            return false;
        }
        if (button == 0 && getSlotUnderMouse(mouseX, mouseY).isPresent()) {
            return false;
        }
        boolean handled = isClickableArea(mouseX, mouseY, button);
        if (!handled || input.isSimulate()) {
            return handled;
        }

        if (button == 0 && handleToolbarClick(mouseX, mouseY)) {
            return true;
        }
        if (button == 0 && materialsTabRect().contains(mouseX, mouseY)) {
            panelTab = PanelTab.MATERIALS;
            return true;
        }
        if (button == 0 && detailsTabRect().contains(mouseX, mouseY)) {
            panelTab = PanelTab.DETAILS;
            return true;
        }
        if (inside(VIEW_X, VIEW_Y, viewWidth, viewHeight, mouseX, mouseY)) {
            autoRotate = false;
            if (button == 0) {
                selectedCell = pickCell(mouseX, mouseY);
                panelTab = selectedCell == null ? PanelTab.MATERIALS : PanelTab.DETAILS;
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean handleMouseDragged(
            double mouseX,
            double mouseY,
            InputConstants.Key key,
            double dragX,
            double dragY) {
        if (key.getType() != InputConstants.Type.MOUSE
                || !inside(VIEW_X, VIEW_Y, viewWidth, viewHeight, mouseX, mouseY)) {
            return false;
        }
        int button = key.getValue();
        if (button == 2 || (button == 0 && Screen.hasShiftDown())) {
            panX += (float) dragX;
            panY += (float) dragY;
            clampPan();
        } else if (button == 0) {
            yaw = wrapDegrees(yaw + (float) dragX * 0.8F);
            pitch = Mth.clamp(pitch + (float) dragY * 0.8F, -85.0F, 85.0F);
        } else {
            return false;
        }
        autoRotate = false;
        return true;
    }

    @Override
    public boolean handleMouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inside(VIEW_X, VIEW_Y, viewWidth, viewHeight, mouseX, mouseY)) {
            float oldZoom = zoom;
            zoom = Mth.clamp((float) (zoom * Math.exp(scrollY * 0.12D)), MIN_ZOOM, MAX_ZOOM);
            float ratio = zoom / oldZoom;
            float relativeX = (float) mouseX - viewCenterX();
            float relativeY = (float) mouseY - viewCenterY();
            panX = relativeX - ratio * (relativeX - panX);
            panY = relativeY - ratio * (relativeY - panY);
            clampPan();
            autoRotate = false;
            return true;
        }
        if (panelTab == PanelTab.MATERIALS
                && inside(panelX, panelContentTop(), PANEL_WIDTH, panelContentHeight(), mouseX, mouseY)) {
            materialScroll = Mth.clamp(
                    materialScroll - (int) Math.round(scrollY * 14.0D),
                    0,
                    maxMaterialScroll());
            return true;
        }
        return false;
    }

    @Override
    public void tick() {
        if (autoRotate) {
            yaw = wrapDegrees(yaw + AUTO_ROTATION_PER_TICK);
        }
    }

    private boolean handleToolbarClick(double mouseX, double mouseY) {
        if (resetButton.contains(mouseX, mouseY)) {
            resetCamera();
            return true;
        }
        if (autoButton.contains(mouseX, mouseY)) {
            autoRotate = !autoRotate;
            return true;
        }
        if (shellButton.contains(mouseX, mouseY)) {
            hideShell = !hideShell;
            return true;
        }
        if (layerModeButton.contains(mouseX, mouseY)) {
            layerMode = layerMode.next();
            return true;
        }
        if (layerDownButton.contains(mouseX, mouseY)) {
            layer = Math.max(0, layer - 1);
            return true;
        }
        if (layerUpButton.contains(mouseX, mouseY)) {
            layer = Math.min(recipe.sizeY() - 1, layer + 1);
            return true;
        }
        return false;
    }

    private boolean isClickableArea(double mouseX, double mouseY, int button) {
        if (button == 2) {
            return inside(VIEW_X, VIEW_Y, viewWidth, viewHeight, mouseX, mouseY);
        }
        return resetButton.contains(mouseX, mouseY)
                || autoButton.contains(mouseX, mouseY)
                || shellButton.contains(mouseX, mouseY)
                || layerModeButton.contains(mouseX, mouseY)
                || layerDownButton.contains(mouseX, mouseY)
                || layerUpButton.contains(mouseX, mouseY)
                || materialsTabRect().contains(mouseX, mouseY)
                || detailsTabRect().contains(mouseX, mouseY)
                || inside(VIEW_X, VIEW_Y, viewWidth, viewHeight, mouseX, mouseY);
    }

    private Component toolbarTooltip(double mouseX, double mouseY) {
        if (resetButton.contains(mouseX, mouseY)) {
            return Component.translatable("jei.ae2lt.multiblock.tooltip.reset");
        }
        if (autoButton.contains(mouseX, mouseY)) {
            return Component.translatable("jei.ae2lt.multiblock.tooltip.auto");
        }
        if (shellButton.contains(mouseX, mouseY)) {
            return Component.translatable("jei.ae2lt.multiblock.tooltip.shell");
        }
        if (layerModeButton.contains(mouseX, mouseY)) {
            return Component.translatable("jei.ae2lt.multiblock.tooltip.layer_mode");
        }
        if (layerDownButton.contains(mouseX, mouseY) || layerUpButton.contains(mouseX, mouseY)
                || layerLabelRect().contains(mouseX, mouseY)) {
            return Component.translatable("jei.ae2lt.multiblock.tooltip.layer");
        }
        return null;
    }

    private MultiblockStructureRecipe.Cell pickCell(double mouseX, double mouseY) {
        MultiblockStructureRecipe.Cell closest = null;
        float closestDepth = Float.NEGATIVE_INFINITY;
        for (var cell : recipe.cells()) {
            if (!isVisible(cell)) {
                continue;
            }
            ProjectedBounds bounds = project(cell);
            float padding = Math.max(1.0F, renderScale() * 0.08F);
            if (mouseX >= bounds.minX() - padding
                    && mouseX <= bounds.maxX() + padding
                    && mouseY >= bounds.minY() - padding
                    && mouseY <= bounds.maxY() + padding
                    && bounds.depth() > closestDepth) {
                closest = cell;
                closestDepth = bounds.depth();
            }
        }
        return closest;
    }

    private ProjectedBounds project(MultiblockStructureRecipe.Cell cell) {
        Matrix4f transform = modelTransform();
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float x = cell.localPos().getX();
        float y = cell.localPos().getY();
        float z = cell.localPos().getZ();
        for (int dx = 0; dx <= 1; dx++) {
            for (int dy = 0; dy <= 1; dy++) {
                for (int dz = 0; dz <= 1; dz++) {
                    Vector3f point = new Vector3f(x + dx, y + dy, z + dz);
                    transform.transformPosition(point);
                    minX = Math.min(minX, point.x);
                    minY = Math.min(minY, point.y);
                    maxX = Math.max(maxX, point.x);
                    maxY = Math.max(maxY, point.y);
                }
            }
        }
        Vector3f center = new Vector3f(x + 0.5F, y + 0.5F, z + 0.5F);
        transform.transformPosition(center);
        return new ProjectedBounds(minX, minY, maxX, maxY, center.z);
    }

    private Matrix4f modelTransform() {
        float scale = renderScale();
        return new Matrix4f()
                .translation(viewCenterX() + panX, viewCenterY() + panY, MODEL_Z)
                .scale(scale, -scale, scale * DEPTH_SCALE_FACTOR)
                .rotateX((float) Math.toRadians(pitch))
                .rotateY((float) Math.toRadians(yaw))
                .translate(-recipe.sizeX() / 2.0F, -recipe.sizeY() / 2.0F, -recipe.sizeZ() / 2.0F);
    }

    private boolean isVisible(MultiblockStructureRecipe.Cell cell) {
        if (cell.state().isAir() || (hideShell && cell.shell())) {
            return false;
        }
        int y = cell.localPos().getY();
        return switch (layerMode) {
            case FULL -> true;
            case UP_TO -> y <= layer;
            case SINGLE -> y == layer;
        };
    }

    private List<Block> visibleAlternatives() {
        if (selectedCell == null) {
            return List.of();
        }
        return selectedCell.alternatives().stream()
                .filter(block -> block != Blocks.AIR)
                .toList();
    }

    private UiRect airAlternativeRect() {
        if (selectedCell == null || !selectedCell.alternatives().contains(Blocks.AIR)) {
            return null;
        }
        int index = Math.min(visibleAlternatives().size(), MAX_ALTERNATIVE_SLOTS);
        return new UiRect(
                panelX + 4 + index % 4 * 23,
                panelContentTop() + 63 + index / 4 * 21,
                18,
                18);
    }

    private void updateDetailSlotOverrides() {
        if (slotsForCell == selectedCell) {
            return;
        }
        slotsForCell = selectedCell;

        selectedBlockSlot.clearDisplayOverrides();
        for (IRecipeSlotDrawable slot : alternativeSlots) {
            slot.clearDisplayOverrides();
        }
        if (selectedCell == null) {
            return;
        }

        selectedBlockSlot.createDisplayOverrides()
                .addItemStack(new ItemStack(selectedCell.state().getBlock()));
        List<Block> alternatives = visibleAlternatives();
        for (int i = 0; i < Math.min(alternatives.size(), alternativeSlots.size()); i++) {
            alternativeSlots.get(i).createDisplayOverrides()
                    .addItemStack(new ItemStack(alternatives.get(i)));
        }
    }

    private void resetCamera() {
        yaw = DEFAULT_YAW;
        pitch = DEFAULT_PITCH;
        zoom = 1.0F;
        panX = 0.0F;
        panY = 0.0F;
    }

    private void clampPan() {
        panX = Mth.clamp(panX, -viewWidth * 0.65F, viewWidth * 0.65F);
        panY = Mth.clamp(panY, -viewHeight * 0.65F, viewHeight * 0.65F);
    }

    private int materialRowY(int index) {
        return panelContentTop() + index * MATERIAL_ROW_HEIGHT - materialScroll;
    }

    private int maxMaterialScroll() {
        return Math.max(0, recipe.materials().size() * MATERIAL_ROW_HEIGHT - panelContentHeight());
    }

    private float renderScale() {
        return baseScale * zoom;
    }

    private float viewCenterX() {
        return VIEW_X + viewWidth / 2.0F;
    }

    private float viewCenterY() {
        return VIEW_Y + viewHeight / 2.0F;
    }

    private int panelContentTop() {
        return panelY + PANEL_TAB_HEIGHT + 2;
    }

    private int panelContentBottom() {
        return panelY + panelHeight - 1;
    }

    private int panelContentHeight() {
        return panelContentBottom() - panelContentTop();
    }

    private UiRect materialsTabRect() {
        return new UiRect(panelX + 1, panelY + 1, (PANEL_WIDTH - 2) / 2, PANEL_TAB_HEIGHT);
    }

    private UiRect detailsTabRect() {
        UiRect materials = materialsTabRect();
        return new UiRect(materials.right(), panelY + 1, PANEL_WIDTH - 2 - materials.width(), PANEL_TAB_HEIGHT);
    }

    private UiRect layerLabelRect() {
        return new UiRect(190, TOOLBAR_Y, 43, TOOLBAR_HEIGHT);
    }

    private void drawButton(
            GuiGraphics guiGraphics,
            Font font,
            UiRect rect,
            Component label,
            boolean active,
            double mouseX,
            double mouseY) {
        int color = active ? ACTIVE_COLOR : rect.contains(mouseX, mouseY) ? HOVER_COLOR : BUTTON_COLOR;
        guiGraphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), color);
        guiGraphics.renderOutline(rect.x(), rect.y(), rect.width(), rect.height(), BORDER_COLOR);
        drawCenteredTrimmed(guiGraphics, font, label, rect, TEXT_COLOR);
    }

    private void drawTab(
            GuiGraphics guiGraphics,
            Font font,
            UiRect rect,
            Component label,
            boolean active,
            double mouseX,
            double mouseY) {
        int color = active ? ACTIVE_COLOR : rect.contains(mouseX, mouseY) ? HOVER_COLOR : BUTTON_COLOR;
        guiGraphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), color);
        guiGraphics.renderOutline(rect.x(), rect.y(), rect.width(), rect.height(), BORDER_COLOR);
        drawCenteredTrimmed(guiGraphics, font, label, rect, active ? TEXT_COLOR : MUTED_TEXT_COLOR);
    }

    private static void drawTrimmed(
            GuiGraphics guiGraphics,
            Font font,
            Component text,
            int x,
            int y,
            int maxWidth,
            int color) {
        String value = text.getString();
        if (font.width(value) > maxWidth) {
            String ellipsis = "...";
            value = font.plainSubstrByWidth(value, Math.max(0, maxWidth - font.width(ellipsis))) + ellipsis;
        }
        guiGraphics.drawString(font, value, x, y, color, false);
    }

    private static void drawCenteredTrimmed(
            GuiGraphics guiGraphics,
            Font font,
            Component text,
            UiRect rect,
            int color) {
        String value = text.getString();
        int maxWidth = Math.max(0, rect.width() - 4);
        if (font.width(value) > maxWidth) {
            value = font.plainSubstrByWidth(value, maxWidth);
        }
        int x = rect.x() + (rect.width() - font.width(value)) / 2;
        int y = rect.y() + (rect.height() - font.lineHeight) / 2 + 1;
        guiGraphics.drawString(font, value, x, y, color, false);
    }

    private static boolean inside(int x, int y, int width, int height, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    /** GuiGraphics scissor coordinates are absolute and do not follow PoseStack translations. */
    private static void enableLocalScissor(
            GuiGraphics guiGraphics,
            int left,
            int top,
            int right,
            int bottom) {
        Matrix4f pose = guiGraphics.pose().last().pose();
        Vector3f first = pose.transformPosition(new Vector3f(left, top, 0.0F));
        Vector3f second = pose.transformPosition(new Vector3f(right, bottom, 0.0F));
        int screenLeft = (int) Math.floor(Math.min(first.x, second.x));
        int screenTop = (int) Math.floor(Math.min(first.y, second.y));
        int screenRight = (int) Math.ceil(Math.max(first.x, second.x));
        int screenBottom = (int) Math.ceil(Math.max(first.y, second.y));
        guiGraphics.enableScissor(screenLeft, screenTop, screenRight, screenBottom);
    }

    private static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0F;
        return wrapped < 0.0F ? wrapped + 360.0F : wrapped;
    }

    private enum LayerMode {
        FULL("jei.ae2lt.multiblock.mode.full"),
        UP_TO("jei.ae2lt.multiblock.mode.up_to"),
        SINGLE("jei.ae2lt.multiblock.mode.single");

        private final String translationKey;

        LayerMode(String translationKey) {
            this.translationKey = translationKey;
        }

        Component label() {
            return Component.translatable(translationKey);
        }

        LayerMode next() {
            return switch (this) {
                case FULL -> UP_TO;
                case UP_TO -> SINGLE;
                case SINGLE -> FULL;
            };
        }
    }

    private enum PanelTab {
        MATERIALS,
        DETAILS
    }

    private record UiRect(int x, int y, int width, int height) {
        int right() {
            return x + width;
        }

        int bottom() {
            return y + height;
        }

        boolean contains(double mouseX, double mouseY) {
            return inside(x, y, width, height, mouseX, mouseY);
        }
    }

    private record ProjectedBounds(float minX, float minY, float maxX, float maxY, float depth) {
    }

}
