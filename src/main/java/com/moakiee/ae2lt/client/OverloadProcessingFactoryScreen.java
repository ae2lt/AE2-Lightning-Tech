package com.moakiee.ae2lt.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import appeng.api.config.ActionItems;
import appeng.client.gui.AEBaseScreen;
import appeng.util.Icon;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ActionButton;
import appeng.client.gui.widgets.ToggleButton;
import appeng.client.gui.widgets.ToolboxPanel;
import appeng.client.gui.widgets.UpgradesPanel;
import appeng.menu.SlotSemantics;

import com.moakiee.ae2lt.client.gui.LargeStackCountRenderer;
import com.moakiee.ae2lt.client.gui.LightningStatusIconWidget;
import com.moakiee.ae2lt.client.gui.LightningStatusLines;
import com.moakiee.ae2lt.menu.OverloadProcessingFactoryMenu;

public class OverloadProcessingFactoryScreen extends AEBaseScreen<OverloadProcessingFactoryMenu> {
    private final ToggleButton autoExportButton;
    private final ActionButton configureOutputButton;
    private final OverloadProcessingFactoryFluidWidget inputFluidWidget;
    private final OverloadProcessingFactoryFluidWidget outputFluidWidget;

    public OverloadProcessingFactoryScreen(
            OverloadProcessingFactoryMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
        this.imageWidth = 176;
        this.imageHeight = 178;

        widgets.add("upgrades", new UpgradesPanel(
                menu.getSlots(SlotSemantics.UPGRADE),
                menu::getCompatibleUpgradeLines));

        // 网络工具适配：玩家手持 NetworkTool 打开 GUI 时显示 toolbox 面板
        if (menu.getToolbox().isPresent()) {
            this.widgets.add("toolbox", new ToolboxPanel(style, menu.getToolbox().getName()));
        }
        addToLeftToolbar(FrequencyBindingClient.createToolbarButton(menu));
        widgets.add("processArea", new OverloadProcessingFactoryProgressWidget(menu, style.getImage("processOverlay")));
        widgets.add("energyBar", new OverloadProcessingFactoryEnergyBar(menu, style.getImage("energyBar")));
        this.inputFluidWidget = new OverloadProcessingFactoryFluidWidget(
                menu,
                0,
                menu::getInputFluid,
                menu::getInputTankCapacity);
        widgets.add("inputFluidBar", this.inputFluidWidget);
        this.outputFluidWidget = new OverloadProcessingFactoryFluidWidget(
                menu,
                1,
                menu::getOutputFluid,
                menu::getOutputTankCapacity);
        widgets.add("outputFluidBar", this.outputFluidWidget);

        this.autoExportButton = new ToggleButton(
                Icon.AUTO_EXPORT_ON,
                Icon.AUTO_EXPORT_OFF,
                state -> menu.clientToggleAutoExport());
        this.autoExportButton.setTooltipOn(List.of(
                Component.translatable("ae2lt.gui.overload_factory.auto_export.title"),
                Component.translatable("ae2lt.gui.overload_factory.auto_export.on")));
        this.autoExportButton.setTooltipOff(List.of(
                Component.translatable("ae2lt.gui.overload_factory.auto_export.title"),
                Component.translatable("ae2lt.gui.overload_factory.auto_export.off")));
        addToLeftToolbar(this.autoExportButton);

        this.configureOutputButton = new ActionButton(
                ActionItems.COG,
                () -> switchToScreen(new OverloadProcessingFactoryOutputConfigScreen(this)));
        this.configureOutputButton.setMessage(
                Component.translatable("ae2lt.gui.overload_factory.configure_output"));
        addToLeftToolbar(this.configureOutputButton);

        widgets.add("lightningStatus", new LightningStatusIconWidget(() -> List.of(
                LightningStatusLines.title(),
                LightningStatusLines.status(menu.isWorking()),
                LightningStatusLines.progress(menu.getProgress()),
                LightningStatusLines.energy(menu.getStoredEnergy(), menu.getEnergyCapacity()),
                LightningStatusLines.highVoltage(menu.getHighVoltageAvailable()),
                LightningStatusLines.extremeHighVoltage(menu.getExtremeHighVoltageAvailable()))));
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        this.autoExportButton.setState(menu.isAutoExportEnabled());
        this.configureOutputButton.setVisibility(menu.isAutoExportEnabled());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (inputFluidWidget != null && inputFluidWidget.isMouseOver(mouseX, mouseY)
                && inputFluidWidget.handleClick(button)) {
            return true;
        }
        if (outputFluidWidget != null && outputFluidWidget.isMouseOver(mouseX, mouseY)
                && outputFluidWidget.handleClick(button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void renderSlot(GuiGraphicsExtractor guiGraphics, Slot slot) {
        super.renderSlot(guiGraphics, slot);
        LargeStackCountRenderer.renderSlotCount(guiGraphics, font, slot);
    }

    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        var lines = super.getTooltipFromContainerItem(stack);
        LargeStackCountRenderer.appendCountTooltip(lines, hoveredSlot);
        return lines;
    }
}
