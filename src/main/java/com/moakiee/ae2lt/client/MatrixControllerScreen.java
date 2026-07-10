package com.moakiee.ae2lt.client;

import com.moakiee.ae2lt.menu.MatrixControllerMenu;
import com.moakiee.ae2lt.network.MatrixControllerActionPacket;
import com.moakiee.ae2lt.logic.craft.MatrixCoreMode;

import java.util.Locale;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class MatrixControllerScreen extends AbstractContainerScreen<MatrixControllerMenu> {
    public MatrixControllerScreen(MatrixControllerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 236;
        imageHeight = 168;
        inventoryLabelY = 10_000;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos + 12;
        int y = topPos + 136;
        addRenderableWidget(actionButton(x, y, 102, "ae2lt.matrix.gui.build", MatrixControllerActionPacket.Action.AUTO_BUILD));
        addRenderableWidget(actionButton(x + 110, y, 102, "ae2lt.matrix.gui.upgrade", MatrixControllerActionPacket.Action.UPGRADE_PATTERN_STORAGE));
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF20242A);
        guiGraphics.fill(leftPos + 4, topPos + 4, leftPos + imageWidth - 4, topPos + imageHeight - 4, 0xFF2D333A);
        guiGraphics.fill(leftPos + 8, topPos + 26, leftPos + imageWidth - 8, topPos + 58, 0xFF171A1F);
        guiGraphics.fill(leftPos + 8, topPos + 64, leftPos + imageWidth - 8, topPos + 100, 0xFF242A31);
        guiGraphics.fill(leftPos + 8, topPos + 106, leftPos + imageWidth - 8, topPos + 128, 0xFF20262D);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, title, 10, 10, 0xE6EEF5, false);
        int y = 32;
        drawLine(guiGraphics, Component.translatable(
                menu.isFormed() ? "ae2lt.matrix.gui.header_formed" : "ae2lt.matrix.gui.header_unformed",
                modeName()), 12, y, menu.isFormed() ? 0x85F29E : 0xF2D37A);

        y += 13;
        drawLine(guiGraphics, Component.translatable("ae2lt.matrix.gui.storage",
                menu.getPatternStorageCount(), menu.getPatternSlotCount()), 12, y, 0xB7C5D3);

        boolean creative = menu.getMode() == MatrixCoreMode.CREATIVE;
        y = 70;
        drawLine(guiGraphics, creative
                ? Component.translatable("ae2lt.matrix.gui.throughput_unbounded")
                : Component.translatable("ae2lt.matrix.gui.throughput", compact(menu.getOperationsPerTick())),
                12, y, 0xE6EEF5);
        y += 12;
        drawLine(guiGraphics, creative
                ? Component.translatable("ae2lt.matrix.gui.heat_ignored")
                : Component.translatable("ae2lt.matrix.gui.heat",
                        percent(menu.getNormalizedHeat()), Component.translatable(heatStateKey())),
                12, y, creative ? 0xFF80C6FF : heatColor());
        if (!creative) {
            drawHeatBar(guiGraphics, 12, 94, imageWidth - 24, 4);
        }

        y = 110;
        if (creative) {
            drawLine(guiGraphics, Component.translatable("ae2lt.matrix.gui.creative_subcores"), 12, y, 0xE6EEF5);
            y += 12;
            drawLine(guiGraphics, Component.translatable("ae2lt.matrix.gui.creative_cpu_cost"),
                    12, y, 0xB7C5D3);
            drawLine(guiGraphics, Component.translatable("ae2lt.matrix.gui.creative_delay"),
                    138, y, 0xB7C5D3);
        } else {
            drawLine(guiGraphics, factorLine(), 12, y, 0xE6EEF5);
            y += 12;
            drawLine(guiGraphics, Component.translatable("ae2lt.matrix.gui.dispatches",
                    fixed(menu.getDispatches())), 12, y, 0xB7C5D3);
            drawLine(guiGraphics, Component.translatable("ae2lt.matrix.gui.batch",
                    fixed(menu.getBaseBatch()), compact(menu.getBatchSize())), 118, y, 0xB7C5D3);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    private Button actionButton(int x, int y, int width, String key, MatrixControllerActionPacket.Action action) {
        return Button.builder(Component.translatable(key), button -> PacketDistributor.sendToServer(
                new MatrixControllerActionPacket(menu.token(), menu.getBlockPos(), action)))
                .bounds(x, y, width, 20)
                .build();
    }

    private void drawLine(GuiGraphics guiGraphics, Component text, int x, int y, int color) {
        guiGraphics.drawString(font, text, x, y, color, false);
    }

    private void drawHeatBar(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        guiGraphics.fill(x, y, x + width, y + height, 0xFF111419);
        int fill = Math.max(0, Math.min(width, (int) Math.round(width * menu.getNormalizedHeat())));
        if (fill > 0) {
            guiGraphics.fill(x, y, x + fill, y + height, heatColor());
        }
        if (menu.getMode() == com.moakiee.ae2lt.logic.craft.MatrixCoreMode.OVERLOAD) {
            int sweetMin = x + (int) Math.round(width * 0.42D);
            int sweetMax = x + (int) Math.round(width * 0.58D);
            guiGraphics.fill(sweetMin, y, sweetMax, y + 1, 0xFF85F29E);
        }
    }

    private Component modeName() {
        return Component.translatable("ae2lt.matrix.mode." + menu.getMode().name().toLowerCase(Locale.ROOT));
    }

    private static String fixed(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String compact(double value) {
        if (Double.isNaN(value) || value <= 0.0D) {
            return "0";
        }
        if (value >= 1_000_000.0D) {
            return String.format(Locale.ROOT, "%.2fM", value / 1_000_000.0D);
        }
        if (value >= 1_000.0D) {
            return String.format(Locale.ROOT, "%.1fk", value / 1_000.0D);
        }
        return String.format(Locale.ROOT, "%.0f", value);
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value * 100.0D);
    }

    private Component factorLine() {
        return switch (menu.getMode()) {
            case STABLE -> Component.translatable("ae2lt.matrix.gui.efficiency", percent(menu.getEfficiencyFactor()));
            case QUANTUM -> Component.translatable("ae2lt.matrix.gui.quantum_factor", fixed(menu.getEfficiencyFactor()));
            case OVERLOAD -> Component.translatable("ae2lt.matrix.gui.overload_factor", fixed(menu.getEfficiencyFactor()));
            default -> Component.translatable("ae2lt.matrix.gui.efficiency", "0.0%");
        };
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

    private int heatColor() {
        return switch (heatStateKey()) {
            case "ae2lt.matrix.gui.heat_sweet", "ae2lt.matrix.gui.heat_good" -> 0xFF85F29E;
            case "ae2lt.matrix.gui.heat_hot" -> 0xFFFF9090;
            case "ae2lt.matrix.gui.heat_cold" -> 0xFF80C6FF;
            default -> 0xF2D37A;
        };
    }
}
