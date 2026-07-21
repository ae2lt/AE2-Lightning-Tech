package com.moakiee.ae2lt.client;

import appeng.api.stacks.AEKey;
import appeng.client.gui.AESubScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.widgets.AECheckbox;
import appeng.client.gui.widgets.TabButton;
import appeng.items.tools.GuideItem;
import appeng.menu.SlotSemantics;
import com.moakiee.ae2lt.config.AE2LTClientConfig;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import guideme.GuidesCommon;
import guideme.PageAnchor;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/** One-time, in-place explanation shown before the maintenance editor is opened. */
final class TianshuMaintenanceIntroScreen<M extends TianshuPatternEncodingTermMenu>
        extends AESubScreen<M, TianshuPatternEncodingTermScreen<M>> {
    private static final PageAnchor GUIDE_PAGE = PageAnchor.page(
            ResourceLocation.fromNamespaceAndPath("ae2lt", "tianshu/pattern-encoding-terminal"));

    private final AEKey target;
    private final AECheckbox dontShowAgain;

    TianshuMaintenanceIntroScreen(TianshuPatternEncodingTermScreen<M> parent, AEKey target) {
        super(parent, "/screens/tianshu_maintenance_intro.json");
        this.target = target;
        hideTerminalSlots();

        dontShowAgain = widgets.addCheckbox("dontShowAgain",
                Component.translatable("ae2lt.tianshu.maintenance.intro.dont_show"), () -> { });
        widgets.addButton("guide", Component.translatable("ae2lt.tianshu.maintenance.intro.guide"),
                this::openGuide);
        widgets.addButton("cancel", Component.translatable("gui.cancel"), this::returnToParent);
        widgets.addButton("continue", Component.translatable("gui.continue"), this::continueToEditor);
        widgets.add("back", new TabButton(
                Icon.BACK, Component.translatable("gui.back"), ignored -> returnToParent()));
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

    private void persistChoice() {
        if (dontShowAgain.isSelected()) AE2LTClientConfig.setShowMaintenanceHelp(false);
    }

    private void continueToEditor() {
        persistChoice();
        getParent().requestMaintenanceEditorFor(target);
        returnToParent();
    }

    private void openGuide() {
        persistChoice();
        GuidesCommon.openGuide(menu.getPlayer(), GuideItem.GUIDE_ID, GUIDE_PAGE);
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(graphics, offsetX, offsetY, mouseX, mouseY);
        graphics.drawString(font,
                Component.translatable("ae2lt.tianshu.maintenance.intro.title"),
                10, 9, 0x30343B, false);
        graphics.renderItem(target.wrapForDisplayOrFilter(), 11, 28);
        graphics.drawString(font,
                font.plainSubstrByWidth(target.getDisplayName().getString(), 176),
                32, 33, 0x30343B, false);
        graphics.drawWordWrap(font,
                Component.translatable("ae2lt.tianshu.maintenance.intro.body"),
                11, 55, 206, 0x505860);
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
