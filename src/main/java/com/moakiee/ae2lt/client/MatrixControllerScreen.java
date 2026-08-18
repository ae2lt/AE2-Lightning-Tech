package com.moakiee.ae2lt.client;
import com.moakiee.ae2lt.network.NetworkInit;

import com.moakiee.ae2lt.logic.craft.MatrixCoreMode;
import com.moakiee.ae2lt.logic.craft.MatrixMultiblockScanIssue;
import com.moakiee.ae2lt.menu.MatrixControllerMenu;
import com.moakiee.ae2lt.network.MatrixControllerActionPacket;

import java.util.Locale;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class MatrixControllerScreen extends MultiblockControllerScreen<MatrixControllerMenu> {

    public MatrixControllerScreen(MatrixControllerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos - 18;
        int y = topPos;

        var build = new TextureToggleButton(
                TextureToggleButton.ButtonType.QUICK_BUILD,
                state -> sendAction(MatrixControllerActionPacket.Action.AUTO_BUILD));
        build.setTooltip(Tooltip.create(Component.translatable("ae2lt.matrix.gui.build")));
        build.setPosition(x, y);
        addRenderableWidget(build);

        var upgrade = new TextureToggleButton(
                TextureToggleButton.ButtonType.PATTERN_STORAGE_UPGRADE,
                state -> sendAction(MatrixControllerActionPacket.Action.UPGRADE_PATTERN_STORAGE));
        upgrade.setTooltip(Tooltip.create(Component.translatable("ae2lt.matrix.gui.upgrade")));
        upgrade.setPosition(x, y + 22);
        addRenderableWidget(upgrade);
    }

    private void sendAction(MatrixControllerActionPacket.Action action) {
        NetworkInit.sendToServer(
                new MatrixControllerActionPacket(menu.token(), menu.getBlockPos(), action));
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        drawTitle(guiGraphics);

        if (!menu.isFormed()) {
            drawStatus(guiGraphics, Component.translatable("ae2lt.matrix.gui.header_unformed"), COL_AMBER);
            drawUnformed(guiGraphics, issueText(), "ae2lt.matrix.gui.hint_unformed");
            return;
        }
        drawStatus(guiGraphics,
                Component.translatable("ae2lt.matrix.gui.header_formed", modeName()), COL_GREEN);

        boolean multidimensional = menu.getMode() == MatrixCoreMode.MULTIDIMENSIONAL;
        int y = ROW_Y;

        drawRow(guiGraphics, y, Component.translatable("ae2lt.matrix.gui.label_throughput"),
                multidimensional ? I18n.get("ae2lt.matrix.gui.value_unbounded")
                        : formatCount(menu.getOperationsPerTick()) + " op/t",
                multidimensional ? COL_BLUE : COL_VALUE);
        y += LINE_H;

        if (!multidimensional) {
            drawRow(guiGraphics, y, Component.translatable("ae2lt.matrix.gui.label_efficiency"),
                    percent(menu.getEfficiencyFactor()), COL_VALUE);
            y += LINE_H;
        }

        drawRow(guiGraphics, y, Component.translatable("ae2lt.matrix.gui.label_patterns"),
                I18n.get("ae2lt.matrix.gui.value_patterns",
                        formatCount(menu.getPatternStorageCount()),
                        formatCount(menu.getPatternSlotCount())),
                COL_VALUE);

        renderFooter(guiGraphics, multidimensional);
    }

    private void renderFooter(GuiGraphics guiGraphics, boolean multidimensional) {
        if (multidimensional) {
            guiGraphics.drawString(font, Component.translatable("ae2lt.matrix.gui.heat_ignored"),
                    TEXT_X, FOOTER_MID_Y, COL_MUTED, false);
            return;
        }
        drawRow(guiGraphics, FOOTER_ROW_Y, Component.translatable("ae2lt.matrix.gui.label_heat"),
                percent(menu.getNormalizedHeat()) + " · " + I18n.get(heatStateKey()),
                heatTextColor());

        boolean overload = menu.getMode() == MatrixCoreMode.OVERLOAD;
        drawGauge(guiGraphics, menu.getNormalizedHeat(), heatBarColor(),
                overload ? 0.42D : -1.0D, overload ? 0.58D : -1.0D);
    }

    private Component modeName() {
        return Component.translatable("ae2lt.matrix.mode." + menu.getMode().name().toLowerCase(Locale.ROOT));
    }

    private Component issueText() {
        int ordinal = menu.getIssue();
        var values = MatrixMultiblockScanIssue.values();
        String name = ordinal >= 0 && ordinal < values.length
                ? values[ordinal].name().toLowerCase(Locale.ROOT)
                : "unknown";
        return Component.translatable("ae2lt.matrix.issue." + name);
    }

    private String heatStateKey() {
        if (!menu.isFormed()) {
            return "ae2lt.matrix.gui.heat_idle";
        }
        return switch (menu.getMode()) {
            case OVERLOAD -> {
                double heat = menu.getNormalizedHeat();
                if (heat < 0.42D) {
                    yield "ae2lt.matrix.gui.heat_cold";
                }
                if (heat > 0.58D) {
                    yield "ae2lt.matrix.gui.heat_hot";
                }
                yield "ae2lt.matrix.gui.heat_sweet";
            }
            case STABLE, QUANTUM -> {
                double heat = menu.getNormalizedHeat();
                if (heat < 0.35D) {
                    yield "ae2lt.matrix.gui.heat_good";
                }
                if (heat < 0.70D) {
                    yield "ae2lt.matrix.gui.heat_warm";
                }
                yield "ae2lt.matrix.gui.heat_hot";
            }
            default -> "ae2lt.matrix.gui.heat_idle";
        };
    }

    private int heatTextColor() {
        return switch (heatStateKey()) {
            case "ae2lt.matrix.gui.heat_sweet", "ae2lt.matrix.gui.heat_good" -> COL_GREEN;
            case "ae2lt.matrix.gui.heat_hot" -> COL_RED;
            case "ae2lt.matrix.gui.heat_cold" -> COL_BLUE;
            default -> COL_AMBER;
        };
    }

    private int heatBarColor() {
        return switch (heatStateKey()) {
            case "ae2lt.matrix.gui.heat_sweet", "ae2lt.matrix.gui.heat_good" -> 0xFF4CBF72;
            case "ae2lt.matrix.gui.heat_hot" -> 0xFFD9534F;
            case "ae2lt.matrix.gui.heat_cold" -> 0xFF5B9BD5;
            default -> 0xFFD8B84E;
        };
    }
}
