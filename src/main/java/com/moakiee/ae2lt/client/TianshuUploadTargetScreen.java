package com.moakiee.ae2lt.client;

import appeng.client.gui.AESubScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.widgets.AETextField;
import appeng.client.gui.widgets.Scrollbar;
import appeng.client.gui.widgets.TabButton;
import appeng.menu.SlotSemantics;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuUploadTargetData;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Native AE2 sub-screen for searching and selecting a pattern-provider group. */
public final class TianshuUploadTargetScreen<M extends TianshuPatternEncodingTermMenu>
        extends AESubScreen<M, TianshuPatternEncodingTermScreen<M>> {
    private static final String STYLE = "/screens/tianshu_upload_targets.json";
    private static final int FIRST_ROW_Y = 43;
    private static final int ROW_HEIGHT = 18;
    private static final int VISIBLE_ROWS = 7;

    private final AETextField search;
    private final Scrollbar scrollbar;
    private int requestedRevision;
    private int focusedIndex = -1;
    private boolean awaitingTargets = true;
    private boolean awaitingUpload;
    private boolean uploadFailed;

    public TianshuUploadTargetScreen(TianshuPatternEncodingTermScreen<M> parent) {
        super(parent, STYLE);
        hideTerminalSlots();
        widgets.add("button_back", new TabButton(Icon.BACK,
                Component.translatable("gui.back"), ignored -> returnToParent()));
        scrollbar = widgets.addScrollBar("scrollbar", Scrollbar.BIG);
        scrollbar.setHeight(VISIBLE_ROWS * ROW_HEIGHT);
        search = widgets.addTextField("field_search");
        search.setMaxLength(96);
        search.setPlaceholder(Component.translatable("ae2lt.tianshu.upload.search"));
        search.setResponder(ignored -> {
            focusedIndex = -1;
            scrollbar.setCurrentScroll(0);
            updateScrollbar();
        });
        requestedRevision = menu.getUploadTargetsRevision();
        menu.requestUploadTargets();
    }

    private void hideTerminalSlots() {
        for (var semantic : List.of(SlotSemantics.CRAFTING_GRID, SlotSemantics.CRAFTING_RESULT,
                SlotSemantics.PROCESSING_INPUTS, SlotSemantics.PROCESSING_OUTPUTS,
                SlotSemantics.SMITHING_TABLE_TEMPLATE, SlotSemantics.SMITHING_TABLE_BASE,
                SlotSemantics.SMITHING_TABLE_ADDITION, SlotSemantics.SMITHING_TABLE_RESULT,
                SlotSemantics.STONECUTTING_INPUT, SlotSemantics.BLANK_PATTERN,
                SlotSemantics.ENCODED_PATTERN, SlotSemantics.PLAYER_INVENTORY,
                SlotSemantics.PLAYER_HOTBAR)) {
            setSlotsHidden(semantic, true);
        }
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(
                Component.translatable("ae2lt.tianshu.upload.refresh"), ignored -> refreshTargets())
                .bounds(leftPos + 8, topPos + 181, 50, 20).build());
        updateScrollbar();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (menu.getUploadTargetsRevision() != requestedRevision) {
            requestedRevision = menu.getUploadTargetsRevision();
            awaitingTargets = false;
            updateScrollbar();
        }
        if (awaitingUpload && menu.uploadState == 1) {
            awaitingUpload = false;
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(
                        Component.translatable("ae2lt.tianshu.upload.success"), true);
            }
            returnToParent();
        } else if (awaitingUpload && menu.uploadState == 3) {
            awaitingUpload = false;
            uploadFailed = true;
            requestedRevision = menu.getUploadTargetsRevision();
            updateScrollbar();
        }
    }

    private void refreshTargets() {
        requestedRevision = menu.getUploadTargetsRevision();
        awaitingTargets = true;
        uploadFailed = false;
        focusedIndex = -1;
        scrollbar.setCurrentScroll(0);
        menu.requestUploadTargets();
    }

    private void updateScrollbar() {
        scrollbar.setRange(0, Math.max(0, filteredTargets().size() - VISIBLE_ROWS), 2);
    }

    private List<IndexedTarget> filteredTargets() {
        String query = search == null ? "" : search.getValue().strip().toLowerCase(Locale.ROOT);
        var result = new ArrayList<IndexedTarget>();
        var targets = menu.getUploadTargets();
        for (int i = 0; i < targets.size(); i++) {
            var target = targets.get(i);
            if (query.isEmpty() || matches(target, query)) result.add(new IndexedTarget(i, target));
        }
        return result;
    }

    private static boolean matches(TianshuUploadTargetData target, String query) {
        var group = target.group();
        if (group.name().getString().toLowerCase(Locale.ROOT).contains(query)) return true;
        if (group.icon() != null
                && group.icon().getId().toString().toLowerCase(Locale.ROOT).contains(query)) return true;
        for (var line : group.tooltip()) {
            if (line.getString().toLowerCase(Locale.ROOT).contains(query)) return true;
        }
        return false;
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(graphics, offsetX, offsetY, mouseX, mouseY);
        var filtered = filteredTargets();
        int start = scrollbar.getCurrentScroll();
        int hovered = hoveredIndex(mouseX, mouseY, filtered.size());
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = start + row;
            if (index >= filtered.size()) break;
            int y = FIRST_ROW_Y + row * ROW_HEIGHT;
            if (index == focusedIndex || index == hovered) {
                graphics.fill(7, y, 151, y + 17, index == focusedIndex ? 0x554477AA : 0x334477AA);
            }
            var target = filtered.get(index).target();
            var group = target.group();
            if (group.icon() != null) graphics.renderItem(group.icon().getReadOnlyStack(), 8, y);
            String count = target.providerCount() > 1 ? " ×" + target.providerCount() : "";
            var name = Component.literal(font.plainSubstrByWidth(group.name().getString(), 91))
                    .append(count);
            graphics.drawString(font, name, 28, y + 4, 0x404040, false);
            String free = Integer.toString(target.availableSlots());
            int freeColor = target.availableSlots() > 0 ? 0x228822 : 0xAA2222;
            graphics.drawString(font, free, 147 - font.width(free), y + 4, freeColor, false);
        }

        Component status;
        int statusColor;
        if (awaitingUpload) {
            status = Component.translatable("ae2lt.tianshu.upload.pending");
            statusColor = 0xAA7700;
        } else if (uploadFailed) {
            status = Component.translatable("ae2lt.tianshu.upload.failed");
            statusColor = 0xAA2222;
        } else if (awaitingTargets) {
            status = Component.translatable("ae2lt.tianshu.upload.loading");
            statusColor = 0x777777;
        } else if (filtered.isEmpty()) {
            status = Component.translatable("ae2lt.tianshu.upload.empty");
            statusColor = 0x777777;
        } else {
            status = Component.translatable("ae2lt.tianshu.upload.hint");
            statusColor = 0x666666;
        }
        graphics.drawString(font, font.plainSubstrByWidth(status.getString(), 111),
                62, 186, statusColor, false);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderTooltip(graphics, mouseX, mouseY);
        var filtered = filteredTargets();
        int index = hoveredIndex(mouseX, mouseY, filtered.size());
        if (index < 0) return;
        var target = filtered.get(index).target();
        var lines = new ArrayList<Component>();
        lines.add(target.group().name());
        lines.addAll(target.group().tooltip());
        lines.add(Component.translatable("ae2lt.tianshu.upload.providers", target.providerCount())
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("ae2lt.tianshu.upload.slots", target.availableSlots())
                .withStyle(target.availableSlots() > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
        drawTooltip(graphics, mouseX, mouseY, lines);
    }

    private int hoveredIndex(double mouseX, double mouseY, int size) {
        double localX = mouseX - leftPos;
        double localY = mouseY - topPos;
        if (localX < 7 || localX >= 151 || localY < FIRST_ROW_Y
                || localY >= FIRST_ROW_Y + VISIBLE_ROWS * ROW_HEIGHT) return -1;
        int index = scrollbar.getCurrentScroll() + (int) ((localY - FIRST_ROW_Y) / ROW_HEIGHT);
        return index >= 0 && index < size ? index : -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && search.isMouseOver(mouseX, mouseY)) {
            search.setValue("");
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && !awaitingUpload) {
            int index = hoveredIndex(mouseX, mouseY, filteredTargets().size());
            if (index >= 0) {
                focusedIndex = index;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && focusedIndex >= 0 && !awaitingUpload) {
            var filtered = filteredTargets();
            int index = hoveredIndex(mouseX, mouseY, filtered.size());
            if (index == focusedIndex) {
                select(filtered.get(index));
                return true;
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void select(IndexedTarget selected) {
        if (selected.target().availableSlots() <= 0) {
            uploadFailed = true;
            return;
        }
        awaitingUpload = true;
        uploadFailed = false;
        menu.uploadTianshuPatternToTarget(selected.target().group());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            returnToParent();
            return true;
        }
        if (search.isFocused()) return super.keyPressed(keyCode, scanCode, modifiers);
        var filtered = filteredTargets();
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && focusedIndex >= 0 && focusedIndex < filtered.size() && !awaitingUpload) {
            select(filtered.get(focusedIndex));
            return true;
        }
        int direction = switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> -1;
            case GLFW.GLFW_KEY_DOWN -> 1;
            default -> 0;
        };
        if (direction == 0 || filtered.isEmpty()) return super.keyPressed(keyCode, scanCode, modifiers);
        focusedIndex = Math.max(0, Math.min(filtered.size() - 1,
                focusedIndex < 0 ? 0 : focusedIndex + direction));
        if (focusedIndex < scrollbar.getCurrentScroll()) {
            scrollbar.setCurrentScroll(focusedIndex);
        } else if (focusedIndex >= scrollbar.getCurrentScroll() + VISIBLE_ROWS) {
            scrollbar.setCurrentScroll(focusedIndex - VISIBLE_ROWS + 1);
        }
        return true;
    }

    private record IndexedTarget(int sourceIndex, TianshuUploadTargetData target) { }
}
