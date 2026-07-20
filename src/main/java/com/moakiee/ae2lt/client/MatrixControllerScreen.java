package com.moakiee.ae2lt.client;

import com.moakiee.ae2lt.menu.MatrixControllerMenu;
import com.moakiee.ae2lt.network.MatrixControllerActionPacket;
import com.moakiee.ae2lt.logic.craft.MatrixCoreMode;
import com.moakiee.ae2lt.logic.craft.MatrixMultiblockScanIssue;
import com.moakiee.ae2lt.logic.compute.ComputeTier;
import com.moakiee.ae2lt.logic.compute.UnifiedCraftingComputeCalculator;

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
        imageWidth = 300;
        imageHeight = 194;
        inventoryLabelY = 10_000;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos + 12;
        int y = topPos + 162;
        addRenderableWidget(actionButton(x, y, 132, "ae2lt.matrix.gui.build", MatrixControllerActionPacket.Action.AUTO_BUILD));
        addRenderableWidget(actionButton(x + 140, y, 132, "ae2lt.matrix.gui.upgrade", MatrixControllerActionPacket.Action.UPGRADE_PATTERN_STORAGE));
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF20242A);
        guiGraphics.fill(leftPos + 4, topPos + 4, leftPos + imageWidth - 4, topPos + imageHeight - 4, 0xFF2D333A);
        guiGraphics.fill(leftPos + 8, topPos + 26, leftPos + imageWidth - 8, topPos + 58, 0xFF171A1F);
        guiGraphics.fill(leftPos + 8, topPos + 64, leftPos + imageWidth - 8, topPos + 154, 0xFF242A31);
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

        if (!menu.isFormed()) {
            drawLine(guiGraphics, issueText(), 12, 70, 0xFFB8A8);
            return;
        }

        boolean creative = menu.getMode() == MatrixCoreMode.CREATIVE;
        ComputeTier tier = computeTier();
        long rawDispatch = UnifiedCraftingComputeCalculator.DISPATCH_PER_UNIT
                * (long) menu.getDispatchUnitCount();
        long dispatchGain = tier == null ? 0L : UnifiedCraftingComputeCalculator.dispatchGain(
                tier, menu.getAmplifierUnitCount());
        long copyGain = tier == null ? 0L : UnifiedCraftingComputeCalculator.copyGain(
                tier, menu.getAmplifierUnitCount());
        y = 70;
        drawLine(guiGraphics, Component.translatable("ae2lt.matrix.gui.units",
                menu.getDispatchUnitCount(), menu.getAmplifierUnitCount(), menu.getCoolingUnitCount(),
                fixed(menu.getCoolingPower())), 12, y, 0xE6EEF5);
        y += 14;
        drawLine(guiGraphics, Component.translatable("ae2lt.matrix.gui.gains",
                1 + menu.getAmplifierUnitCount(), formatBudget(dispatchGain),
                creative ? "∞" : formatBudget(copyGain)), 12, y, 0xB7C5D3);
        y += 14;
        drawLine(guiGraphics, Component.translatable("ae2lt.matrix.gui.raw_dispatch",
                formatBudget(rawDispatch)), 12, y, 0xB7C5D3);
        y += 14;
        drawLine(guiGraphics, creative
                ? Component.translatable("ae2lt.matrix.gui.throughput_unbounded")
                : Component.translatable("ae2lt.matrix.gui.throughput", compact(menu.getOperationsPerTick())),
                12, y, 0xE6EEF5);
        y += 14;
        drawLine(guiGraphics, creative
                ? Component.translatable("ae2lt.matrix.gui.heat_ignored")
                : Component.translatable("ae2lt.matrix.gui.thermal",
                        percent(menu.getNormalizedHeat()), percent(menu.getEfficiencyFactor()),
                        Component.translatable(heatStateKey())),
                12, y, creative ? 0xFF80C6FF : heatColor());
        y += 14;
        drawLine(guiGraphics, Component.translatable("ae2lt.matrix.gui.provider_calls",
                menu.getProviderCallsRemaining()), 12, y, 0xB7C5D3);
        if (!creative) {
            drawHeatBar(guiGraphics, 12, 151, imageWidth - 24, 3);
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

    private Component issueText() {
        int ordinal = menu.getIssue();
        var values = MatrixMultiblockScanIssue.values();
        String name = ordinal >= 0 && ordinal < values.length
                ? values[ordinal].name().toLowerCase(Locale.ROOT)
                : "unknown";
        return Component.translatable("ae2lt.matrix.issue." + name);
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

    private ComputeTier computeTier() {
        return switch (menu.getMode()) {
            case STABLE -> ComputeTier.BASELINE;
            case QUANTUM -> ComputeTier.QUANTUM;
            case OVERLOAD -> ComputeTier.OVERLOAD;
            case CREATIVE -> ComputeTier.MULTIDIMENSIONAL;
            default -> null;
        };
    }

    private static String formatBudget(long value) {
        return value == Long.MAX_VALUE || value == Integer.MAX_VALUE
                ? "∞" : String.format(Locale.ROOT, "%,d", value);
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
