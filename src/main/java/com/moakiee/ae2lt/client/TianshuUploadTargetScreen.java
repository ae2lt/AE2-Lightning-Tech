package com.moakiee.ae2lt.client;

import appeng.api.config.Settings;
import appeng.api.config.TerminalStyle;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.client.gui.AESubScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.widgets.AETextField;
import appeng.client.gui.widgets.IconButton;
import appeng.client.gui.widgets.Scrollbar;
import appeng.client.gui.widgets.SettingToggleButton;
import appeng.client.gui.widgets.TabButton;
import appeng.core.AEConfig;
import appeng.menu.SlotSemantics;
import com.moakiee.ae2lt.config.AE2LTClientConfig;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuUploadTargetData;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/** EA2-native provider picker modelled after EAEP ClientPlus' provider-list sub-screen. */
public final class TianshuUploadTargetScreen<M extends TianshuPatternEncodingTermMenu>
        extends AESubScreen<M, TianshuPatternEncodingTermScreen<M>> {
    private static final String STYLE = "/screens/tianshu_upload_targets.json";
    private static final int GUI_WIDTH = 195;
    private static final int GUI_HEADER_HEIGHT = 34;
    private static final int GUI_FOOTER_HEIGHT = 8;
    private static final int GUI_VERTICAL_PADDING = 54;
    private static final int ROW_HEIGHT = 18;
    private static final int ROW_LEFT = 7;
    private static final int ROW_RIGHT = 169;

    private final AETextField sourceField;
    private final AETextField aliasField;
    private final Scrollbar scrollbar;
    private final Map<Integer, Component> queries = new LinkedHashMap<>();
    private final List<IndexedTarget> filtered = new ArrayList<>();
    private final String primarySourceKey;

    private int visibleRows;
    private int selectedQueryIndex;
    private int focusedIndex = -1;
    private int requestedRevision;
    private String customQuery = "";
    private boolean queryRefresh = true;
    private boolean updatingFields;
    private boolean awaitingTargets = true;
    private boolean awaitingUpload;
    private boolean uploadFailed;

    public TianshuUploadTargetScreen(TianshuPatternEncodingTermScreen<M> parent) {
        super(parent, STYLE);
        imageWidth = GUI_WIDTH;

        addToLeftToolbar(new SettingToggleButton<>(Settings.TERMINAL_STYLE,
                AEConfig.instance().getTerminalStyle(), this::toggleTerminalStyle));
        widgets.add("button_back", new TabButton(Icon.BACK,
                Component.translatable("gui.back"), ignored -> returnToParent()));
        addToLeftToolbar(new AliasActionButton(Icon.ENTER,
                Component.translatable("ae2lt.tianshu.upload.alias.add"), this::addMapping));
        addToLeftToolbar(new AliasActionButton(Icon.CLEAR,
                Component.translatable("ae2lt.tianshu.upload.alias.remove"), this::removeMappings));

        scrollbar = widgets.addScrollBar("scrollbar", Scrollbar.BIG);
        sourceField = widgets.addTextField("field_search");
        sourceField.setMaxLength(256);
        sourceField.setPlaceholder(Component.translatable("ae2lt.tianshu.upload.keyword"));
        aliasField = widgets.addTextField("field_alias");
        aliasField.setMaxLength(256);
        aliasField.setPlaceholder(Component.translatable("ae2lt.tianshu.upload.alias"));

        primarySourceKey = collectQueries();
        sourceField.setValue(primarySourceKey);
        String storedAlias = AE2LTClientConfig.findUploadAlias(primarySourceKey);
        selectedQueryIndex = storedAlias == null ? 0 : 1;
        if (storedAlias != null) queries.put(1, Component.literal(storedAlias));
        aliasField.setValue(selectedQuery().getString());

        sourceField.setResponder(value -> {
            if (updatingFields || !aliasField.getValue().isBlank()) return;
            selectedQueryIndex = -1;
            customQuery = value;
            queryRefresh = true;
        });
        aliasField.setResponder(value -> {
            if (updatingFields) return;
            selectedQueryIndex = -1;
            customQuery = value;
            queryRefresh = true;
        });

        requestedRevision = menu.getUploadTargetsRevision();
        menu.requestUploadTargets();
    }

    private String collectQueries() {
        queries.put(0, Component.empty());
        ItemStack stack = menu.getSlots(SlotSemantics.ENCODED_PATTERN).stream()
                .map(slot -> slot.getItem()).filter(item -> !item.isEmpty())
                .findFirst().orElse(ItemStack.EMPTY);
        var player = Minecraft.getInstance().player;
        if (stack.isEmpty() || player == null) return "";
        var details = PatternDetailsHelper.decodePattern(stack, player.level());
        var output = details == null ? null : details.getPrimaryOutput();
        if (output == null || output.what() == null) return "";
        var key = output.what();
        String id = key.getId().toString();
        queries.put(2, key.getDisplayName());
        queries.put(3, Component.literal(key.getModId()));
        return id;
    }

    @Override
    protected void init() {
        visibleRows = Math.max(6, config.getTerminalStyle().getRows(
                (height - GUI_HEADER_HEIGHT - GUI_FOOTER_HEIGHT - GUI_VERTICAL_PADDING) / ROW_HEIGHT));
        imageHeight = GUI_HEADER_HEIGHT + GUI_FOOTER_HEIGHT + visibleRows * ROW_HEIGHT;
        scrollbar.setHeight(visibleRows * ROW_HEIGHT);
        super.init();
        queryRefresh = true;
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (menu.getUploadTargetsRevision() != requestedRevision) {
            requestedRevision = menu.getUploadTargetsRevision();
            awaitingTargets = false;
            queryRefresh = true;
        }
        if (awaitingUpload && menu.uploadState == 1) {
            awaitingUpload = false;
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(
                        Component.translatable("ae2lt.tianshu.upload.success"), true);
            }
            returnToParent();
            return;
        }
        if (awaitingUpload && menu.uploadState == 3) {
            awaitingUpload = false;
            uploadFailed = true;
            requestedRevision = menu.getUploadTargetsRevision();
            queryRefresh = true;
        }
        if (queryRefresh) {
            queryRefresh = false;
            if (selectedQueryIndex >= 0) {
                updatingFields = true;
                aliasField.setValue(selectedQuery().getString());
                updatingFields = false;
            }
            rebuildCandidateTooltip();
            rebuildFilteredTargets();
        }
    }

    private void rebuildFilteredTargets() {
        String query = selectedQuery().getString().strip().toLowerCase(Locale.ROOT);
        filtered.clear();
        var targets = menu.getUploadTargets();
        for (int i = 0; i < targets.size(); i++) {
            var target = targets.get(i);
            if (query.isEmpty() || matches(target, query)) filtered.add(new IndexedTarget(i, target));
        }
        focusedIndex = filtered.isEmpty() ? -1 : 0;
        scrollbar.setRange(0, Math.max(0, filtered.size() - visibleRows), 2);
    }

    private static boolean matches(TianshuUploadTargetData target, String query) {
        var group = target.group();
        if (group.name().getString().toLowerCase(Locale.ROOT).contains(query)) return true;
        if (group.icon() != null) {
            if (group.icon().getId().toString().toLowerCase(Locale.ROOT).contains(query)) return true;
            if (group.icon().getDisplayName().getString().toLowerCase(Locale.ROOT).contains(query)) return true;
        }
        for (var line : group.tooltip()) {
            if (line.getString().toLowerCase(Locale.ROOT).contains(query)) return true;
        }
        return false;
    }

    private Component selectedQuery() {
        return queries.getOrDefault(selectedQueryIndex, Component.literal(customQuery));
    }

    private void rebuildCandidateTooltip() {
        var lines = new ArrayList<Component>();
        lines.add(Component.translatable("ae2lt.tianshu.upload.alias.candidates")
                .withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
        for (var entry : queries.entrySet()) {
            Component value = entry.getValue().getString().isEmpty()
                    ? Component.translatable("ae2lt.tianshu.upload.alias.all") : entry.getValue();
            lines.add(Component.literal(entry.getKey() == selectedQueryIndex ? "\u2192 " : "  ")
                    .withStyle(entry.getKey() == selectedQueryIndex
                            ? ChatFormatting.GREEN : ChatFormatting.GRAY)
                    .append(value));
        }
        aliasField.setTooltipMessage(lines);
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        int textColor = style.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB();
        int start = scrollbar.getCurrentScroll();
        for (int row = 0; row < visibleRows; row++) {
            int index = start + row;
            if (index >= filtered.size()) break;
            var target = filtered.get(index).target();
            int y = GUI_HEADER_HEIGHT + row * ROW_HEIGHT;
            if (target.group().icon() != null) {
                graphics.renderItem(target.group().icon().getReadOnlyStack(), ROW_LEFT + 2, y);
            }
            String suffix = " [\u2248" + target.availableSlots() + "]";
            var label = target.group().name().copy().append(suffix);
            graphics.drawString(font, Language.getInstance().getVisualOrder(
                            font.substrByWidth(label, 145)), ROW_LEFT + 22,
                    y + 4, target.availableSlots() > 0 ? textColor : 0xFFAA3333, false);
        }

        Component status = null;
        int statusColor = 0xFF777777;
        if (awaitingUpload) {
            status = Component.translatable("ae2lt.tianshu.upload.pending");
            statusColor = 0xFFAA7700;
        } else if (uploadFailed) {
            status = Component.translatable("ae2lt.tianshu.upload.failed");
            statusColor = 0xFFAA2222;
        } else if (awaitingTargets) {
            status = Component.translatable("ae2lt.tianshu.upload.loading");
        } else if (filtered.isEmpty()) {
            status = Component.translatable("ae2lt.tianshu.upload.empty");
        }
        if (status != null) {
            String text = font.plainSubstrByWidth(status.getString(), 158);
            graphics.drawString(font, text, (GUI_WIDTH - font.width(text)) / 2,
                    GUI_HEADER_HEIGHT + visibleRows * ROW_HEIGHT - 14, statusColor, false);
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

        int start = scrollbar.getCurrentScroll();
        for (int row = 0; row < visibleRows; row++) {
            int index = start + row;
            int top = offsetY + GUI_HEADER_HEIGHT + row * ROW_HEIGHT;
            int color = index == focusedIndex ? 0xFF78B9E6
                    : (row & 1) == 0 ? 0xFFB4B5C6 : 0xFF989AAC;
            graphics.fill(offsetX + ROW_LEFT, top, offsetX + ROW_RIGHT, top + ROW_HEIGHT - 1, color);
            graphics.fill(offsetX + ROW_LEFT, top, offsetX + ROW_RIGHT, top + 1, 0xFFE8E8F0);
        }
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderTooltip(graphics, mouseX, mouseY);
        int index = hoveredIndex(mouseX, mouseY);
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

    private int hoveredIndex(double mouseX, double mouseY) {
        double x = mouseX - leftPos;
        double y = mouseY - topPos - GUI_HEADER_HEIGHT;
        if (x < ROW_LEFT || x >= ROW_RIGHT || y < 0 || y >= visibleRows * ROW_HEIGHT) return -1;
        int index = scrollbar.getCurrentScroll() + (int) (y / ROW_HEIGHT);
        return index >= 0 && index < filtered.size() ? index : -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (sourceField.isMouseOver(mouseX, mouseY)) {
                sourceField.setValue("");
                return true;
            }
            if (aliasField.isMouseOver(mouseX, mouseY)) {
                aliasField.setValue("");
                return true;
            }
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && !awaitingUpload) {
            int index = hoveredIndex(mouseX, mouseY);
            if (index >= 0) {
                focusedIndex = index;
                playDownSound(Minecraft.getInstance().getSoundManager());
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && focusedIndex >= 0 && !awaitingUpload) {
            int index = hoveredIndex(mouseX, mouseY);
            if (index == focusedIndex) {
                select(filtered.get(index));
                return true;
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (queries.size() > 1 && aliasField.isMouseOver(mouseX, mouseY)) {
            var keys = new ArrayList<>(queries.keySet());
            int position = keys.indexOf(selectedQueryIndex);
            if (position < 0) position = 0;
            position = Math.floorMod(position + (deltaY > 0 ? -1 : 1), keys.size());
            selectedQueryIndex = keys.get(position);
            queryRefresh = true;
            if (minecraft.player != null) {
                minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.1F, 1.0F);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && focusedIndex >= 0 && focusedIndex < filtered.size() && !awaitingUpload) {
            select(filtered.get(focusedIndex));
            return true;
        }
        if (filtered.isEmpty()) return super.keyPressed(keyCode, scanCode, modifiers);
        int direction = switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> -1;
            case GLFW.GLFW_KEY_DOWN -> 1;
            default -> 0;
        };
        if (direction == 0) return super.keyPressed(keyCode, scanCode, modifiers);
        focusedIndex = Math.max(0, Math.min(filtered.size() - 1,
                focusedIndex < 0 ? 0 : focusedIndex + direction));
        if (focusedIndex < scrollbar.getCurrentScroll()) {
            scrollbar.setCurrentScroll(focusedIndex);
        } else if (focusedIndex >= scrollbar.getCurrentScroll() + visibleRows) {
            scrollbar.setCurrentScroll(focusedIndex - visibleRows + 1);
        }
        return true;
    }

    @Override
    public void changeFocus(ComponentPath path) {
        super.changeFocus(path);
        focusedIndex = -1;
    }

    @Override
    public void onClose() {
        returnToParent();
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

    private void addMapping() {
        String source = sourceField.getValue().strip();
        String alias = aliasField.getValue().strip();
        if (!AE2LTClientConfig.setUploadAlias(source, alias)) {
            showMessage(Component.translatable(source.isEmpty()
                    ? "ae2lt.tianshu.upload.alias.empty_keyword"
                    : "ae2lt.tianshu.upload.alias.empty_alias"));
            return;
        }
        queries.put(1, Component.literal(alias));
        selectedQueryIndex = 1;
        queryRefresh = true;
        showMessage(Component.translatable("ae2lt.tianshu.upload.alias.added", source, alias));
    }

    private void removeMappings() {
        String alias = aliasField.getValue().strip();
        if (alias.isEmpty()) {
            showMessage(Component.translatable("ae2lt.tianshu.upload.alias.empty_alias"));
            return;
        }
        int removed = AE2LTClientConfig.removeUploadAliases(alias);
        if (removed <= 0) {
            showMessage(Component.translatable("ae2lt.tianshu.upload.alias.not_found", alias));
            return;
        }
        queries.remove(1);
        selectedQueryIndex = 0;
        queryRefresh = true;
        showMessage(Component.translatable("ae2lt.tianshu.upload.alias.removed", removed, alias));
    }

    private void showMessage(Component message) {
        if (minecraft.player != null) minecraft.player.displayClientMessage(message, false);
    }

    private void toggleTerminalStyle(SettingToggleButton<TerminalStyle> button, boolean backwards) {
        var next = button.getNextValue(backwards);
        AEConfig.instance().setTerminalStyle(next);
        button.set(next);
        children().removeIf(child -> renderables.contains(child));
        renderables.clear();
        init();
    }

    private static void playDownSound(SoundManager soundManager) {
        soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private record IndexedTarget(int sourceIndex, TianshuUploadTargetData target) { }

    private static final class AliasActionButton extends IconButton {
        private final Icon icon;

        private AliasActionButton(Icon icon, Component message, Runnable action) {
            super(ignored -> action.run());
            this.icon = icon;
            setMessage(message);
        }

        @Override
        protected Icon getIcon() {
            return icon;
        }
    }
}
