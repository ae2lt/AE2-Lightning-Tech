package com.moakiee.ae2lt.client;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.AESubScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.widgets.Scrollbar;
import appeng.client.gui.widgets.TabButton;
import appeng.menu.SlotSemantics;
import com.moakiee.ae2lt.logic.tianshu.terminal.ProcessingPatternEncodingType;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.pedroksl.advanced_ae.AdvancedAE;
import net.pedroksl.advanced_ae.client.gui.widgets.DirectionInputButton;
import net.pedroksl.advanced_ae.common.definitions.AAEText;

/**
 * Popup arming the next processing encode as an AdvancedAE pattern.
 * Reuses AdvancedAE's direction widgets; only construct when AdvancedAE is loaded.
 */
final class TianshuAdvancedPatternConfigScreen<M extends TianshuPatternEncodingTermMenu>
        extends AESubScreen<M, TianshuPatternEncodingTermScreen<M>> {
    private static final int GUI_WIDTH = 195;
    private static final int HEADER_HEIGHT = 20;
    private static final int ROW_HEIGHT = 20;
    private static final int VISIBLE_ROWS = 5;
    private static final int FOOTER_HEIGHT = 26;
    private static final int ROW_LEFT = 8;
    private static final int ROW_RIGHT = 169;
    private static final int BUTTONS_LEFT = 34;
    private static final int DIR_BUTTON_WIDTH = 12;
    private static final int DIR_BUTTON_HEIGHT = 14;
    private static final int DIR_COUNT = 7;

    private final Scrollbar scrollbar;
    private final List<Row> rows = new ArrayList<>();
    private final List<DirectionInputButton[]> rowButtons = new ArrayList<>();

    TianshuAdvancedPatternConfigScreen(TianshuPatternEncodingTermScreen<M> parent) {
        super(parent, "/screens/tianshu_advanced_pattern_config.json");
        imageWidth = GUI_WIDTH;
        imageHeight = HEADER_HEIGHT + VISIBLE_ROWS * ROW_HEIGHT + FOOTER_HEIGHT;

        widgets.add("button_back", new TabButton(Icon.BACK,
                Component.translatable("gui.back"), ignored -> returnToParent()));
        widgets.addButton("save",
                Component.translatable("ae2lt.tianshu.pattern_config.done"), this::confirm);
        scrollbar = widgets.addScrollBar("scrollbar", Scrollbar.SMALL);

        collectRows();
    }

    /** One row per distinct input key; AdvancedAE stores directions per key, not per slot. */
    private void collectRows() {
        var config = menu.getAdvancedEncodingConfig();
        var pending = new LinkedHashMap<AEKey, Integer>();
        var slots = menu.getProcessingInputSlots();
        for (int i = 0; i < slots.length; i++) {
            var stack = GenericStack.fromItemStack(slots[i].getItem());
            if (stack == null || stack.what() == null) continue;
            pending.putIfAbsent(stack.what(), config != null ? config.direction(i) : 0);
        }
        pending.forEach((key, direction) -> rows.add(new Row(key, direction)));
    }

    @Override
    protected void init() {
        super.init();
        rowButtons.clear();
        for (var row : rows) {
            var buttons = new DirectionInputButton[DIR_COUNT];
            for (int i = 0; i < DIR_COUNT; i++) {
                var button = new DirectionInputButton(0, 0, DIR_BUTTON_WIDTH, DIR_BUTTON_HEIGHT,
                        directionTextures(i), this::directionPressed);
                button.setTooltip(Tooltip.create(directionLabel(i)));
                button.setKey(row.key);
                button.setIndex(i);
                button.visible = false;
                buttons[i] = addRenderableWidget(button);
            }
            rowButtons.add(buttons);
        }
        scrollbar.setHeight(VISIBLE_ROWS * ROW_HEIGHT - 2);
        scrollbar.setRange(0, Math.max(0, rows.size() - VISIBLE_ROWS), 2);
        setSlotsHidden(SlotSemantics.TOOLBOX, true);
    }

    private void directionPressed(Button pressed) {
        var button = (DirectionInputButton) pressed;
        var direction = button.getDirection();
        int encoded = direction == null ? 0 : direction.ordinal() + 1;
        for (var row : rows) {
            if (row.key.equals(button.getKey())) {
                row.direction = encoded;
                break;
            }
        }
    }

    private void confirm() {
        if (rows.isEmpty()) {
            returnToParent();
            return;
        }
        var slots = menu.getProcessingInputSlots();
        var directions = new int[slots.length];
        for (int i = 0; i < slots.length; i++) {
            var stack = GenericStack.fromItemStack(slots[i].getItem());
            if (stack == null || stack.what() == null) continue;
            for (var row : rows) {
                if (row.key.equals(stack.what())) {
                    directions[i] = row.direction;
                    break;
                }
            }
        }
        menu.armAdvancedEncoding(new ProcessingPatternEncodingType.AdvancedConfig(directions));
        returnToParent();
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        for (var buttons : rowButtons) {
            for (var button : buttons) button.visible = false;
        }
        int scroll = scrollbar.getCurrentScroll();
        for (int visible = 0; visible < VISIBLE_ROWS; visible++) {
            int index = scroll + visible;
            if (index >= rows.size()) break;
            var row = rows.get(index);
            int rowY = HEADER_HEIGHT + visible * ROW_HEIGHT;
            graphics.renderItem(row.key.wrapForDisplayOrFilter(), ROW_LEFT + 2, rowY + 2);
            var buttons = rowButtons.get(index);
            int highlighted = columnForDirection(row.direction);
            for (int col = 0; col < DIR_COUNT; col++) {
                var button = buttons[col];
                button.setPosition(leftPos + BUTTONS_LEFT + col * (DIR_BUTTON_WIDTH + 1),
                        topPos + rowY + 3);
                button.setHighlighted(col == highlighted);
                button.visible = true;
            }
        }
        if (rows.isEmpty()) {
            var text = Component.translatable("ae2lt.tianshu.pattern_config.empty");
            graphics.drawString(font, text, (GUI_WIDTH - font.width(text)) / 2,
                    HEADER_HEIGHT + (VISIBLE_ROWS * ROW_HEIGHT) / 2 - 4, 0xFF777777, false);
        }
    }

    @Override
    public void drawBG(GuiGraphics graphics, int offsetX, int offsetY,
                       int mouseX, int mouseY, float partialTicks) {
        int bottom = offsetY + imageHeight;
        graphics.fill(offsetX, offsetY, offsetX + GUI_WIDTH, bottom, 0xFFC6C6D2);
        graphics.fill(offsetX, offsetY, offsetX + GUI_WIDTH, offsetY + 1, 0xFFFFFFFF);
        graphics.fill(offsetX, offsetY, offsetX + 1, bottom, 0xFFFFFFFF);
        graphics.fill(offsetX + GUI_WIDTH - 1, offsetY + 1, offsetX + GUI_WIDTH, bottom, 0xFF555563);
        graphics.fill(offsetX + 1, bottom - 1, offsetX + GUI_WIDTH, bottom, 0xFF555563);
        for (int visible = 0; visible < VISIBLE_ROWS; visible++) {
            int top = offsetY + HEADER_HEIGHT + visible * ROW_HEIGHT;
            int color = (visible & 1) == 0 ? 0xFFB4B5C6 : 0xFF989AAC;
            graphics.fill(offsetX + ROW_LEFT - 1, top, offsetX + ROW_RIGHT, top + ROW_HEIGHT - 1, color);
            graphics.fill(offsetX + ROW_LEFT - 1, top, offsetX + ROW_RIGHT, top + 1, 0xFFE8E8F0);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (deltaY != 0 && rows.size() > VISIBLE_ROWS) {
            scrollbar.setCurrentScroll(scrollbar.getCurrentScroll() + (deltaY > 0 ? -1 : 1));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public void onClose() {
        returnToParent();
    }

    private static int columnForDirection(int direction) {
        if (direction <= 0 || direction > 6) return 0;
        return switch (Direction.values()[direction - 1]) {
            case NORTH -> 1;
            case EAST -> 2;
            case SOUTH -> 3;
            case WEST -> 4;
            case UP -> 5;
            case DOWN -> 6;
        };
    }

    private static Pair<ResourceLocation, ResourceLocation> directionTextures(int index) {
        var name = switch (index) {
            case 1 -> "north";
            case 2 -> "east";
            case 3 -> "south";
            case 4 -> "west";
            case 5 -> "up";
            case 6 -> "down";
            default -> "any";
        };
        return new Pair<>(
                AdvancedAE.makeId("textures/guis/" + name + "_button.png"),
                AdvancedAE.makeId("textures/guis/" + name + "_button_selected.png"));
    }

    private static Component directionLabel(int index) {
        return Component.translatable(switch (index) {
            case 1 -> AAEText.NorthButton.getTranslationKey();
            case 2 -> AAEText.EastButton.getTranslationKey();
            case 3 -> AAEText.SouthButton.getTranslationKey();
            case 4 -> AAEText.WestButton.getTranslationKey();
            case 5 -> AAEText.UpButton.getTranslationKey();
            case 6 -> AAEText.DownButton.getTranslationKey();
            default -> AAEText.AnyButton.getTranslationKey();
        });
    }

    /** direction: 0 = any side, otherwise Direction.ordinal() + 1. */
    private static final class Row {
        final AEKey key;
        int direction;

        Row(AEKey key, int direction) {
            this.key = key;
            this.direction = direction;
        }
    }
}
