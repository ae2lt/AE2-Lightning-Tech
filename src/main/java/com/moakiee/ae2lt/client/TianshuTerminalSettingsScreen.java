package com.moakiee.ae2lt.client;

import appeng.client.gui.AESubScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.me.common.TerminalSettingsScreen;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.TabButton;
import appeng.menu.SlotSemantics;
import appeng.menu.me.common.MEStorageMenu;
import com.moakiee.ae2lt.config.AE2LTClientConfig;
import com.moakiee.ae2lt.config.TianshuUploadTrigger;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import com.moakiee.ae2lt.menu.Ae2ltSlotSemantics;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Tianshu-specific settings linked from AE2's terminal settings screen. */
public final class TianshuTerminalSettingsScreen<M extends MEStorageMenu>
        extends AESubScreen<M, TerminalSettingsScreen<M>> {
    private AE2Button triggerButton;
    private AE2Button duplicateEncodingButton;
    private AE2Button wirelessSupplyButton;
    private final boolean patternTerminal;

    public TianshuTerminalSettingsScreen(TerminalSettingsScreen<M> parent) {
        super(parent, parent.getMenu() instanceof TianshuPatternEncodingTermMenu
                ? "/screens/tianshu_terminal_settings.json" : "/screens/tianshu_crafting_settings.json");
        patternTerminal = parent.getMenu() instanceof TianshuPatternEncodingTermMenu;
        hideTerminalSlots();
        widgets.add("back", new TabButton(Icon.BACK,
                Component.translatable("gui.back"), ignored -> returnToParent()));
        if (patternTerminal) {
            triggerButton = widgets.addButton("uploadTrigger", triggerLabel(), this::cycleTrigger);
            duplicateEncodingButton = widgets.addButton(
                    "duplicateEncoding", duplicateEncodingLabel(), this::toggleDuplicateEncoding);
        }
        wirelessSupplyButton = widgets.addButton("jeiWirelessSupply", wirelessSupplyLabel(), () -> {
            AE2LTClientConfig.setJeiWirelessSupply(!AE2LTClientConfig.jeiWirelessSupply());
            if (net.neoforged.fml.ModList.get().isLoaded("jei")) JeiWirelessSupplyClient.clear();
            wirelessSupplyButton.setMessage(wirelessSupplyLabel());
        });
        wirelessSupplyButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("ae2lt.tianshu.settings.jei_supply.hint")));
    }

    private void hideTerminalSlots() {
        for (var semantic : List.of(SlotSemantics.CRAFTING_GRID, SlotSemantics.CRAFTING_RESULT,
                SlotSemantics.PROCESSING_INPUTS, SlotSemantics.PROCESSING_OUTPUTS,
                SlotSemantics.SMITHING_TABLE_TEMPLATE, SlotSemantics.SMITHING_TABLE_BASE,
                SlotSemantics.SMITHING_TABLE_ADDITION, SlotSemantics.SMITHING_TABLE_RESULT,
                SlotSemantics.STONECUTTING_INPUT, SlotSemantics.BLANK_PATTERN,
                SlotSemantics.ENCODED_PATTERN, SlotSemantics.PLAYER_INVENTORY,
                SlotSemantics.PLAYER_HOTBAR, Ae2ltSlotSemantics.TIANSHU_SMITHING,
                Ae2ltSlotSemantics.TIANSHU_ANVIL, Ae2ltSlotSemantics.TIANSHU_STONECUTTING,
                Ae2ltSlotSemantics.TIANSHU_CELL, Ae2ltSlotSemantics.TIANSHU_CELL_UPGRADE,
                Ae2ltSlotSemantics.TIANSHU_CELL_CONFIG, Ae2ltSlotSemantics.TIANSHU_CLOSED_LOOP_MEMBER,
                Ae2ltSlotSemantics.TIANSHU_CLOSED_LOOP_OUTPUT_MARK, Ae2ltSlotSemantics.TIANSHU_GLOBAL_RESERVE_MARK,
                Ae2ltSlotSemantics.TIANSHU_OMNIVERSAL_INPUTS, Ae2ltSlotSemantics.TIANSHU_OMNIVERSAL_OUTPUTS,
                Ae2ltSlotSemantics.TIANSHU_OMNIVERSAL_MOLDS)) {
            setSlotsHidden(semantic, true);
        }
    }

    private void cycleTrigger() {
        TianshuUploadTrigger next = AE2LTClientConfig.uploadTrigger().next();
        AE2LTClientConfig.setUploadTrigger(next);
        triggerButton.setMessage(triggerLabel());
    }

    private Component triggerLabel() {
        return Component.translatable("ae2lt.tianshu.settings.trigger."
                + AE2LTClientConfig.uploadTrigger().name().toLowerCase(java.util.Locale.ROOT));
    }

    private void toggleDuplicateEncoding() {
        AE2LTClientConfig.setInterceptDuplicatePatternEncoding(
                !AE2LTClientConfig.interceptDuplicatePatternEncoding());
        duplicateEncodingButton.setMessage(duplicateEncodingLabel());
    }

    private Component duplicateEncodingLabel() {
        return Component.translatable(AE2LTClientConfig.interceptDuplicatePatternEncoding()
                ? "ae2lt.tianshu.settings.duplicate_encoding.on"
                : "ae2lt.tianshu.settings.duplicate_encoding.off");
    }

    private Component wirelessSupplyLabel() {
        return Component.translatable(AE2LTClientConfig.jeiWirelessSupply()
                ? "ae2lt.tianshu.settings.jei_supply.on" : "ae2lt.tianshu.settings.jei_supply.off");
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(graphics, offsetX, offsetY, mouseX, mouseY);
        if (patternTerminal) {
            graphics.drawString(font, Component.translatable("ae2lt.tianshu.settings.upload_trigger"),
                    10, 30, 0x404040, false);
            graphics.drawWordWrap(font,
                    Component.translatable("ae2lt.tianshu.settings.upload_trigger.hint"),
                    10, 72, 180, 0x666666);
            graphics.drawString(font,
                    Component.translatable("ae2lt.tianshu.settings.duplicate_encoding"),
                    10, 118, 0x404040, false);
            graphics.drawWordWrap(font,
                    Component.translatable("ae2lt.tianshu.settings.duplicate_encoding.hint"),
                    10, 160, 180, 0x666666);
        }
        graphics.drawString(font, Component.translatable("ae2lt.tianshu.settings.jei_supply"),
                10, patternTerminal ? 202 : 30, 0x404040, false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            returnToParent();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
