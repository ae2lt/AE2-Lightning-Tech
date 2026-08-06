package com.moakiee.ae2lt.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import appeng.api.config.ActionItems;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ActionButton;
import appeng.client.gui.widgets.ToggleButton;

import com.moakiee.ae2lt.client.gui.LargeStackCountRenderer;
import com.moakiee.ae2lt.client.gui.LightningStatusIconWidget;
import com.moakiee.ae2lt.client.gui.LightningStatusLines;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.Mode;
import com.moakiee.ae2lt.menu.CrystalCatalyzerMenu;

public class CrystalCatalyzerScreen extends AEBaseScreen<CrystalCatalyzerMenu> {
    private final ToggleButton autoExportButton;
    private final ActionButton configureOutputButton;
    private final TextureToggleButton modeButton;
    private final CrystalCatalyzerFluidWidget fluidWidget;

    public CrystalCatalyzerScreen(
            CrystalCatalyzerMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
        this.imageWidth = 176;
        this.imageHeight = 190;

        addToLeftToolbar(FrequencyBindingClient.createToolbarButton(menu));

        widgets.add("energyBar", new CrystalCatalyzerEnergyBar(menu, style.getImage("energyBar")));
        this.fluidWidget = new CrystalCatalyzerFluidWidget(
                menu,
                menu::getFluid,
                menu::getFluidCapacity);
        widgets.add("fluidBar", this.fluidWidget);
        widgets.add("processArea", new CrystalCatalyzerProgressWidget(menu, style.getImage("processOverlay")));

        this.autoExportButton = new ToggleButton(
                Icon.AUTO_EXPORT_ON,
                Icon.AUTO_EXPORT_OFF,
                state -> menu.clientToggleAutoExport());
        this.autoExportButton.setTooltipOn(List.of(
                Component.translatable("ae2lt.gui.crystal_catalyzer.auto_export.title"),
                Component.translatable("ae2lt.gui.crystal_catalyzer.auto_export.on")));
        this.autoExportButton.setTooltipOff(List.of(
                Component.translatable("ae2lt.gui.crystal_catalyzer.auto_export.title"),
                Component.translatable("ae2lt.gui.crystal_catalyzer.auto_export.off")));
        addToLeftToolbar(this.autoExportButton);

        this.configureOutputButton = new ActionButton(
                ActionItems.COG,
                () -> switchToScreen(new CrystalCatalyzerOutputConfigScreen(this)));
        this.configureOutputButton.setMessage(
                Component.translatable("ae2lt.gui.crystal_catalyzer.configure_output"));
        addToLeftToolbar(this.configureOutputButton);

        this.modeButton = new TextureToggleButton(
                TextureToggleButton.ButtonType.CRYSTAL_CATALYZER_MODE, btn -> menu.clientCycleMode());
        this.modeButton.setTooltipOff(List.of(
                Component.translatable("ae2lt.gui.crystal_catalyzer.mode.title"),
                Component.translatable("ae2lt.gui.crystal_catalyzer.mode.crystal"),
                Component.translatable("ae2lt.gui.crystal_catalyzer.mode.tooltip.crystal")));
        this.modeButton.setTooltipOn(List.of(
                Component.translatable("ae2lt.gui.crystal_catalyzer.mode.title"),
                Component.translatable("ae2lt.gui.crystal_catalyzer.mode.dust"),
                Component.translatable("ae2lt.gui.crystal_catalyzer.mode.tooltip.dust")));
        addToLeftToolbar(this.modeButton);

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
        this.modeButton.setState(menu.getMode() == Mode.DUST);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // AE2 的 WidgetContainer 会吞掉非左键事件;在此抢先拦截 tank 区域的右键/中键
        // 用于"右键倒入 / shift+右键清空"等 Adv AE 风格交互。
        if (fluidWidget != null && fluidWidget.isMouseOver(mouseX, mouseY)) {
            if (fluidWidget.handleClick(button)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void renderSlot(GuiGraphics guiGraphics, Slot slot) {
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
