package com.moakiee.ae2lt.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.core.localization.GuiText;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.menu.MatrixPortMenu;

/**
 * Searchable, scrollable view of every pattern held by a matter warping matrix.
 *
 * <p>The search behavior mirrors ExtendedAE's assembler matrix: a row remains visible if any
 * pattern in it has an input or output whose display name matches the ordered search tokens.
 * Matching patterns are highlighted while the other patterns in that retained row are dimmed.</p>
 */
public class MatrixPortScreen extends AbstractContainerScreen<MatrixPortMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            AE2LightningTech.MODID, "textures/guis/matrix_pattern_manager.png");

    private static final int TEXTURE_SIZE = 256;
    private static final int GUI_WIDTH = 190;
    private static final int GUI_HEIGHT = 223;
    private static final int SLOT_SPACING = 18;
    private static final int OFFSCREEN_SLOT = -10_000;

    private static final int SEARCH_X = 78;
    private static final int SEARCH_Y = 7;
    private static final int SEARCH_WIDTH = 94;
    private static final int SEARCH_HEIGHT = 11;

    private static final int SCROLLBAR_X = 175;
    private static final int SCROLLBAR_Y = 22;
    private static final int SCROLLBAR_WIDTH = 12;
    private static final int SCROLLBAR_HEIGHT = 103;
    private static final int HANDLE_HEIGHT = 15;
    private static final int HANDLE_U_NORMAL = 192;
    private static final int HANDLE_U_HOVERED = 206;
    private static final int HANDLE_V = 4;

    private static final int PATTERN_AREA_X = 12;
    private static final int PATTERN_AREA_Y = 20;
    private static final int PATTERN_AREA_WIDTH = 175;
    private static final int PATTERN_AREA_HEIGHT = 108;

    private final List<Integer> filteredRows = new ArrayList<>();
    private final Set<Integer> matchingPatternSlots = new HashSet<>();
    private final Map<String, Boolean> nameMatchCache = new HashMap<>();

    private EditBox searchField;
    private int scrollRow;
    private boolean draggingScrollbar;
    private long lastContentFingerprint;

    public MatrixPortScreen(MatrixPortMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = GUI_WIDTH;
        imageHeight = GUI_HEIGHT;
        titleLabelY = 10_000;
        inventoryLabelY = 10_000;
    }

    @Override
    protected void init() {
        super.init();
        searchField = new EditBox(
                font,
                leftPos + SEARCH_X,
                topPos + SEARCH_Y,
                SEARCH_WIDTH,
                SEARCH_HEIGHT,
                Component.translatable("ae2lt.gui.matrix_port.search"));
        searchField.setBordered(false);
        searchField.setMaxLength(64);
        searchField.setTextColor(0xF2F2F2);
        searchField.setHint(GuiText.SearchPlaceholder.text().copy().setStyle(
                Style.EMPTY.withColor(TextColor.fromRgb(0xDEDFE3))));
        searchField.setResponder(ignored -> refreshList(true));
        addRenderableWidget(searchField);
        setInitialFocus(searchField);
        refreshList(true);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (searchField != null && !searchField.getValue().isBlank()) {
            long fingerprint = contentFingerprint();
            if (fingerprint != lastContentFingerprint) {
                refreshList(false);
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(
                TEXTURE,
                leftPos,
                topPos,
                0,
                0,
                imageWidth,
                imageHeight,
                TEXTURE_SIZE,
                TEXTURE_SIZE);
        renderSearchHighlights(graphics);
        renderScrollbar(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(
                font,
                Component.translatable("ae2lt.gui.matrix_port.title"),
                7,
                7,
                0x404040,
                false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1 && searchField != null && searchField.isMouseOver(mouseX, mouseY)) {
            searchField.setValue("");
        }
        if (button == 0 && isWithinScrollbar(mouseX, mouseY) && maxScrollRow() > 0) {
            draggingScrollbar = true;
            updateScrollFromMouse(mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX,
                                double mouseY,
                                int button,
                                double dragX,
                                double dragY) {
        if (draggingScrollbar) {
            updateScrollFromMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX,
                                 double mouseY,
                                 double scrollX,
                                 double scrollY) {
        if (isWithinPatternArea(mouseX, mouseY) && maxScrollRow() > 0 && scrollY != 0.0D) {
            setScrollRow(scrollRow - (int) Math.signum(scrollY));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void refreshList(boolean resetScroll) {
        filteredRows.clear();
        matchingPatternSlots.clear();
        nameMatchCache.clear();

        List<String> searchTokens = tokenize(searchField == null ? "" : searchField.getValue());
        int rowCount = (menu.getPatternSlotCount() + MatrixPortMenu.COLUMNS - 1)
                / MatrixPortMenu.COLUMNS;
        for (int row = 0; row < rowCount; row++) {
            if (searchTokens.isEmpty() || rowMatches(row, searchTokens)) {
                filteredRows.add(row);
            }
        }

        if (resetScroll) {
            scrollRow = 0;
        } else {
            scrollRow = Mth.clamp(scrollRow, 0, maxScrollRow());
        }
        updateVisibleSlots();
        lastContentFingerprint = contentFingerprint();
    }

    private boolean rowMatches(int row, List<String> searchTokens) {
        boolean rowMatches = false;
        int firstSlot = row * MatrixPortMenu.COLUMNS;
        int lastSlot = Math.min(firstSlot + MatrixPortMenu.COLUMNS, menu.getPatternSlotCount());
        for (int slotIndex = firstSlot; slotIndex < lastSlot; slotIndex++) {
            ItemStack stack = menu.getPatternSlots().get(slotIndex).getItem();
            if (patternMatches(stack, searchTokens)) {
                matchingPatternSlots.add(slotIndex);
                rowMatches = true;
            }
        }
        return rowMatches;
    }

    private boolean patternMatches(ItemStack patternStack, List<String> searchTokens) {
        if (patternStack.isEmpty() || minecraft == null || minecraft.level == null) {
            return false;
        }

        final IPatternDetails details;
        try {
            details = PatternDetailsHelper.decodePattern(patternStack, minecraft.level);
        } catch (RuntimeException ignored) {
            return false;
        }
        if (details == null) {
            return false;
        }

        for (var output : details.getOutputs()) {
            if (output != null && nameMatches(output.what().getDisplayName().getString(), searchTokens)) {
                return true;
            }
        }
        for (var input : details.getInputs()) {
            if (input == null) {
                continue;
            }
            var possibleInputs = input.getPossibleInputs();
            if (possibleInputs.length > 0
                    && possibleInputs[0] != null
                    && nameMatches(possibleInputs[0].what().getDisplayName().getString(), searchTokens)) {
                return true;
            }
        }
        return false;
    }

    private boolean nameMatches(String displayName, List<String> searchTokens) {
        return nameMatchCache.computeIfAbsent(displayName, name -> {
            if (compareTokens(searchTokens, tokenize(name))) {
                return true;
            }
            String compactQuery = String.join("", searchTokens);
            return JecSearchCompat.contains(name.toLowerCase(Locale.ROOT), compactQuery);
        });
    }

    private static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        var tokens = new ArrayList<String>();
        for (String token : text.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    /** Ordered partial-token comparison used by ExtendedAE's assembler-matrix search. */
    private static boolean compareTokens(List<String> filter, List<String> target) {
        int start = 0;
        while (start <= target.size() - filter.size()) {
            int targetIndex = start;
            int filterIndex = 0;
            while (targetIndex < target.size() && filterIndex < filter.size()) {
                if (target.get(targetIndex).contains(filter.get(filterIndex))) {
                    filterIndex++;
                }
                targetIndex++;
            }
            if (filterIndex >= filter.size()) {
                return true;
            }
            start++;
        }
        return false;
    }

    private void updateVisibleSlots() {
        var slots = menu.getPatternSlots();
        for (var slot : slots) {
            slot.setActive(false);
            slot.x = OFFSCREEN_SLOT;
            slot.y = OFFSCREEN_SLOT;
        }

        int visibleRows = Math.min(MatrixPortMenu.VISIBLE_ROWS, filteredRows.size() - scrollRow);
        for (int visibleRow = 0; visibleRow < visibleRows; visibleRow++) {
            int patternRow = filteredRows.get(scrollRow + visibleRow);
            int firstSlot = patternRow * MatrixPortMenu.COLUMNS;
            int lastSlot = Math.min(firstSlot + MatrixPortMenu.COLUMNS, slots.size());
            for (int slotIndex = firstSlot; slotIndex < lastSlot; slotIndex++) {
                int column = slotIndex - firstSlot;
                var slot = slots.get(slotIndex);
                slot.x = MatrixPortMenu.PATTERN_X + column * SLOT_SPACING;
                slot.y = MatrixPortMenu.PATTERN_Y + visibleRow * SLOT_SPACING;
                slot.setActive(true);
            }
        }
    }

    private void renderSearchHighlights(GuiGraphics graphics) {
        if (searchField == null || searchField.getValue().isBlank()) {
            return;
        }
        for (var slot : menu.getPatternSlots()) {
            if (!slot.isActive()) {
                continue;
            }
            int color = matchingPatternSlots.contains(slot.getPatternIndex())
                    ? 0x8A00FF00
                    : 0x6A000000;
            graphics.fill(
                    leftPos + slot.x,
                    topPos + slot.y,
                    leftPos + slot.x + 16,
                    topPos + slot.y + 16,
                    color);
        }
    }

    private void renderScrollbar(GuiGraphics graphics, int mouseX, int mouseY) {
        int handleY = handleY();
        boolean hovered = draggingScrollbar || isMouseOverHandle(mouseX, mouseY, handleY);
        graphics.blit(
                TEXTURE,
                leftPos + SCROLLBAR_X,
                topPos + handleY,
                hovered ? HANDLE_U_HOVERED : HANDLE_U_NORMAL,
                HANDLE_V,
                SCROLLBAR_WIDTH,
                HANDLE_HEIGHT,
                TEXTURE_SIZE,
                TEXTURE_SIZE);
    }

    private int handleY() {
        int maxScroll = maxScrollRow();
        if (maxScroll <= 0) {
            return SCROLLBAR_Y;
        }
        int travel = SCROLLBAR_HEIGHT - HANDLE_HEIGHT;
        return SCROLLBAR_Y + Math.round((float) scrollRow / maxScroll * travel);
    }

    private void updateScrollFromMouse(double mouseY) {
        int maxScroll = maxScrollRow();
        if (maxScroll <= 0) {
            setScrollRow(0);
            return;
        }
        int travel = SCROLLBAR_HEIGHT - HANDLE_HEIGHT;
        double relative = mouseY - (topPos + SCROLLBAR_Y) - HANDLE_HEIGHT / 2.0D;
        double fraction = Mth.clamp(relative / travel, 0.0D, 1.0D);
        setScrollRow((int) Math.round(fraction * maxScroll));
    }

    private void setScrollRow(int value) {
        int clamped = Mth.clamp(value, 0, maxScrollRow());
        if (clamped != scrollRow) {
            scrollRow = clamped;
            updateVisibleSlots();
        }
    }

    private int maxScrollRow() {
        return Math.max(0, filteredRows.size() - MatrixPortMenu.VISIBLE_ROWS);
    }

    private boolean isWithinScrollbar(double mouseX, double mouseY) {
        return mouseX >= leftPos + SCROLLBAR_X
                && mouseX < leftPos + SCROLLBAR_X + SCROLLBAR_WIDTH
                && mouseY >= topPos + SCROLLBAR_Y
                && mouseY < topPos + SCROLLBAR_Y + SCROLLBAR_HEIGHT;
    }

    private boolean isMouseOverHandle(double mouseX, double mouseY, int handleY) {
        return mouseX >= leftPos + SCROLLBAR_X
                && mouseX < leftPos + SCROLLBAR_X + SCROLLBAR_WIDTH
                && mouseY >= topPos + handleY
                && mouseY < topPos + handleY + HANDLE_HEIGHT;
    }

    private boolean isWithinPatternArea(double mouseX, double mouseY) {
        return mouseX >= leftPos + PATTERN_AREA_X
                && mouseX < leftPos + PATTERN_AREA_X + PATTERN_AREA_WIDTH
                && mouseY >= topPos + PATTERN_AREA_Y
                && mouseY < topPos + PATTERN_AREA_Y + PATTERN_AREA_HEIGHT;
    }

    private long contentFingerprint() {
        long hash = 1L;
        for (var slot : menu.getPatternSlots()) {
            hash = 31L * hash + ItemStack.hashItemAndComponents(slot.getItem());
        }
        return hash;
    }
}
