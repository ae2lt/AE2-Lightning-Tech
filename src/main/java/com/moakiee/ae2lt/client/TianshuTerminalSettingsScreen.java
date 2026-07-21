package com.moakiee.ae2lt.client;

import appeng.client.gui.AESubScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.me.common.TerminalSettingsScreen;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.AECheckbox;
import appeng.client.gui.widgets.TabButton;
import appeng.menu.SlotSemantics;
import com.moakiee.ae2lt.config.AE2LTClientConfig;
import com.moakiee.ae2lt.config.TianshuUploadTrigger;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Tianshu-specific settings linked from AE2's terminal settings screen. */
public final class TianshuTerminalSettingsScreen<M extends TianshuPatternEncodingTermMenu>
        extends AESubScreen<M, TerminalSettingsScreen<M>> {
    private AE2Button triggerButton;
    private AECheckbox maintenanceHelp;

    public TianshuTerminalSettingsScreen(TerminalSettingsScreen<M> parent) {
        super(parent, "/screens/tianshu_terminal_settings.json");
        hideTerminalSlots();
        widgets.add("back", new TabButton(Icon.BACK,
                Component.translatable("gui.back"), ignored -> returnToParent()));
        triggerButton = widgets.addButton("uploadTrigger", triggerLabel(), this::cycleTrigger);
        maintenanceHelp = widgets.addCheckbox("maintenanceHelp",
                Component.translatable("ae2lt.tianshu.settings.maintenance_help"),
                () -> AE2LTClientConfig.setShowMaintenanceHelp(maintenanceHelp.isSelected()));
        maintenanceHelp.setSelected(AE2LTClientConfig.showMaintenanceHelp());
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

    private void cycleTrigger() {
        TianshuUploadTrigger next = AE2LTClientConfig.uploadTrigger().next();
        AE2LTClientConfig.setUploadTrigger(next);
        triggerButton.setMessage(triggerLabel());
    }

    private Component triggerLabel() {
        return Component.translatable("ae2lt.tianshu.settings.trigger."
                + AE2LTClientConfig.uploadTrigger().name().toLowerCase(java.util.Locale.ROOT));
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(graphics, offsetX, offsetY, mouseX, mouseY);
        graphics.drawString(font, Component.translatable("ae2lt.tianshu.settings.upload_trigger"),
                10, 30, 0x404040, false);
        graphics.drawWordWrap(font,
                Component.translatable("ae2lt.tianshu.settings.upload_trigger.hint"),
                10, 96, 180, 0x666666);
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
