package com.moakiee.ae2lt.client;

import com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockScanIssue;
import com.moakiee.ae2lt.menu.TianshuSupercomputerControllerMenu;
import com.moakiee.ae2lt.network.TianshuControllerActionPacket;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class TianshuSupercomputerControllerScreen
        extends MultiblockControllerScreen<TianshuSupercomputerControllerMenu> {

    private TextureToggleButton fastPlanningButton;

    public TianshuSupercomputerControllerScreen(
            TianshuSupercomputerControllerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos - 18;
        int y = topPos;

        var build = new TextureToggleButton(
                TextureToggleButton.ButtonType.QUICK_BUILD,
                state -> sendAction(TianshuControllerActionPacket.Action.AUTO_BUILD));
        build.setTooltipAt(0, List.of(Component.translatable("ae2lt.tianshu.gui.build")));
        build.setPosition(x, y);
        addRenderableWidget(build);

        fastPlanningButton = new TextureToggleButton(
                TextureToggleButton.ButtonType.QUICK_COMPUTE,
                state -> sendAction(TianshuControllerActionPacket.Action.TOGGLE_FAST_PLANNING));
        fastPlanningButton.setTooltipOff(List.of(
                Component.translatable("ae2lt.tianshu.gui.fast_planning.off")));
        fastPlanningButton.setTooltipOn(List.of(
                Component.translatable("ae2lt.tianshu.gui.fast_planning.on")));
        fastPlanningButton.setPosition(x, y + 22);
        refreshFastPlanningButton();
        addRenderableWidget(fastPlanningButton);
    }

    private void sendAction(TianshuControllerActionPacket.Action action) {
        PacketDistributor.sendToServer(
                new TianshuControllerActionPacket(menu.token(), menu.getBlockPos(), action));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (fastPlanningButton != null) {
            refreshFastPlanningButton();
        }
    }

    private void refreshFastPlanningButton() {
        fastPlanningButton.setState(menu.isFastPlanningEnabled());
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        drawTitle(graphics);

        if (!menu.isFormed()) {
            drawStatus(graphics, Component.translatable("ae2lt.tianshu.gui.unformed"), COL_AMBER);
            drawUnformed(graphics, issueText(), "ae2lt.matrix.gui.hint_unformed");
            return;
        }
        drawStatus(graphics, Component.translatable("ae2lt.tianshu.gui.formed", tierName()), COL_GREEN);

        int y = ROW_Y;
        drawRow(graphics, y, Component.translatable("ae2lt.tianshu.gui.label_storage"),
                formatStorage(menu.getStorageBytes()), COL_VALUE);
        y += LINE_H;

        boolean capped = menu.isCapped();
        drawRow(graphics, y, Component.translatable("ae2lt.tianshu.gui.label_dispatches"),
                formatCount(menu.getSuccessfulDispatchesPerTick()) + "/t"
                        + (capped ? I18n.get("ae2lt.tianshu.gui.capped") : ""),
                capped ? COL_AMBER : COL_VALUE);
        y += LINE_H;

        drawRow(graphics, y, Component.translatable("ae2lt.tianshu.gui.label_loop"),
                I18n.get("ae2lt.tianshu.gui.value_loop",
                        formatCount(menu.getClosedLoopPatternStorages()),
                        formatCount(menu.getClosedLoopSeedStorages())),
                COL_VALUE);

        boolean fast = menu.isFastPlanningEnabled();
        drawRow(graphics, FOOTER_MID_Y, Component.translatable("ae2lt.tianshu.gui.label_fast"),
                I18n.get(fast ? "ae2lt.tianshu.gui.fast_on" : "ae2lt.tianshu.gui.fast_off"),
                fast ? COL_GREEN : COL_MUTED);
    }

    private Component issueText() {
        int ordinal = menu.getIssue();
        var values = TianshuMultiblockScanIssue.values();
        String name = ordinal >= 0 && ordinal < values.length
                ? values[ordinal].name().toLowerCase(Locale.ROOT) : "unknown";
        return Component.translatable("ae2lt.tianshu.issue." + name);
    }

    private Component tierName() {
        var tier = menu.getTier();
        return tier == null ? Component.literal("—")
                : Component.translatable("ae2lt.tianshu.tier." + tier.name().toLowerCase(Locale.ROOT));
    }

    private static String formatStorage(long bytes) {
        if (bytes == Long.MAX_VALUE) return "∞";
        if (bytes >= 1024L * 1024L * 1024L) return String.format(Locale.ROOT, "%.2f GiB", bytes / (1024.0 * 1024 * 1024));
        return String.format(Locale.ROOT, "%.0f MiB", bytes / (1024.0 * 1024));
    }

}
