package com.moakiee.ae2lt.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import appeng.api.config.ActionItems;
import appeng.api.upgrades.Upgrades;
import appeng.client.gui.AEBaseScreen;
import appeng.util.Icon;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ActionButton;
import appeng.client.gui.widgets.ToggleButton;
import appeng.client.gui.widgets.ToolboxPanel;
import appeng.client.gui.widgets.UpgradesPanel;
import appeng.core.localization.GuiText;
import appeng.menu.SlotSemantics;

import com.moakiee.ae2lt.client.gui.LargeStackCountRenderer;
import com.moakiee.ae2lt.client.gui.LightningStatusIconWidget;
import com.moakiee.ae2lt.client.gui.LightningStatusLines;
import com.moakiee.ae2lt.menu.LightningSimulationChamberMenu;

public class LightningSimulationChamberScreen extends AEBaseScreen<LightningSimulationChamberMenu> {
    private final LightningSimulationEnergyBar energyBar;
    private final LightningSimulationProcessWidget processWidget;
    private final ToggleButton autoExportButton;
    private final ActionButton configureOutputButton;

    public LightningSimulationChamberScreen(
            LightningSimulationChamberMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);

        widgets.add("upgrades", new UpgradesPanel(
                menu.getSlots(SlotSemantics.UPGRADE),
                this::getCompatibleUpgrades));

        // 网络工具适配：玩家手持 NetworkTool 打开 GUI 时显示 toolbox 面板
        if (menu.getToolbox().isPresent()) {
            this.widgets.add("toolbox", new ToolboxPanel(style, menu.getToolbox().getName()));
        }

        addToLeftToolbar(FrequencyBindingClient.createToolbarButton(menu));

        this.processWidget = new LightningSimulationProcessWidget(menu, style.getImage("processOverlay"));
        widgets.add("processArea", processWidget);

        this.energyBar = new LightningSimulationEnergyBar(menu, style.getImage("energyBar"));
        widgets.add("energyBar", energyBar);

        widgets.add("lightningStatus", new LightningStatusIconWidget(() -> List.of(
                LightningStatusLines.title(),
                LightningStatusLines.status(menu.isWorking()),
                LightningStatusLines.progress(menu.getProgress()),
                LightningStatusLines.energy(menu.getStoredEnergy(), menu.getEnergyCapacity()),
                LightningStatusLines.highVoltage(menu.getHighVoltageAvailable()),
                LightningStatusLines.extremeHighVoltage(menu.getExtremeHighVoltageAvailable()),
                menu.getMatrixMessage(),
                menu.getLightningDemandMessage(),
                menu.getSubstitutionMessage())));

        this.autoExportButton = new ToggleButton(
                Icon.AUTO_EXPORT_ON,
                Icon.AUTO_EXPORT_OFF,
                state -> menu.clientToggleAutoExport());
        this.autoExportButton.setTooltipOn(List.of(
                Component.translatable("ae2lt.gui.lightning_simulation.auto_export.title"),
                Component.translatable("ae2lt.gui.lightning_simulation.auto_export.on")));
        this.autoExportButton.setTooltipOff(List.of(
                Component.translatable("ae2lt.gui.lightning_simulation.auto_export.title"),
                Component.translatable("ae2lt.gui.lightning_simulation.auto_export.off")));
        addToLeftToolbar(this.autoExportButton);

        this.configureOutputButton = new ActionButton(
                ActionItems.COG,
                () -> switchToScreen(new LightningSimulationOutputConfigScreen(this)));
        this.configureOutputButton.setMessage(
                Component.translatable("ae2lt.gui.lightning_simulation.configure_output"));
        addToLeftToolbar(this.configureOutputButton);
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

    private List<Component> getCompatibleUpgrades() {
        var list = new ArrayList<Component>();
        list.add(GuiText.CompatibleUpgrades.text());
        list.addAll(Upgrades.getTooltipLinesForMachine(menu.getHost().getUpgrades().getUpgradableItem()));
        return list;
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        this.autoExportButton.setState(menu.isAutoExportEnabled());
        this.configureOutputButton.setVisibility(menu.isAutoExportEnabled());
    }

}
