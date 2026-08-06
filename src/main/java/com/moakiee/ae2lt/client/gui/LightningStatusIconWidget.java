package com.moakiee.ae2lt.client.gui;

import java.util.List;
import java.util.function.Supplier;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import appeng.client.gui.widgets.ITooltip;

/**
 * 机器 GUI 上的闪电状态图标 widget(16×16)。
 *
 * <p>以标准 AE2 widget 的方式挂到 {@link appeng.client.gui.AEBaseScreen},
 * 位置由 {@code ae2/screens/*.json} 里 {@code widgets.lightningStatus} 配置,
 * 悬停时通过 {@link ITooltip} 机制显示机器状态 / 网络内闪电库存等信息。</p>
 *
 * <p>原本这些信息是直接 {@code drawString} 画在 GUI 空白区里的,
 * 会遮挡槽位和美工设计元素,现在统一收敛到这个图标的 tooltip 里。</p>
 */
public class LightningStatusIconWidget extends AbstractWidget implements ITooltip {

    public static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath(
            "ae2lt", "textures/gui/buttons/lightning.png");
    public static final int SIZE = 16;

    private final Supplier<List<Component>> tooltipSupplier;

    public LightningStatusIconWidget(Supplier<List<Component>> tooltipSupplier) {
        super(0, 0, SIZE, SIZE, Component.empty());
        this.tooltipSupplier = tooltipSupplier;
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.blit(ICON, getX(), getY(), 0, 0, SIZE, SIZE, SIZE, SIZE);
    }

    @Override
    public List<Component> getTooltipMessage() {
        List<Component> lines = tooltipSupplier.get();
        return lines != null ? lines : List.of();
    }

    @Override
    public Rect2i getTooltipArea() {
        return new Rect2i(getX(), getY(), width, height);
    }

    @Override
    public boolean isTooltipAreaVisible() {
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
    }
}
