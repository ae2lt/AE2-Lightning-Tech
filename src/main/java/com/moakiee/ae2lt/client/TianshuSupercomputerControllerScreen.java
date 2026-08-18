package com.moakiee.ae2lt.client;

import com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockScanIssue;
import com.moakiee.ae2lt.menu.TianshuSupercomputerControllerMenu;
import com.moakiee.ae2lt.network.TianshuControllerActionPacket;

import java.util.Locale;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import appeng.client.gui.Icon;
import appeng.client.gui.widgets.TabButton;
import appeng.core.network.serverbound.SwitchGuisPacket;
import appeng.menu.implementations.PriorityMenu;

public class TianshuSupercomputerControllerScreen
        extends MultiblockControllerScreen<TianshuSupercomputerControllerMenu> {

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
        build.setTooltip(Tooltip.create(Component.translatable("ae2lt.tianshu.gui.build")));
        build.setPosition(x, y);
        addRenderableWidget(build);

        var selection = new TextureToggleButton(
                TextureToggleButton.ButtonType.CPU_SELECTION,
                state -> sendAction(TianshuControllerActionPacket.Action.OPEN_ALGORITHM_SELECTION));
        selection.setTooltip(Tooltip.create(Component.translatable(
                "ae2lt.tianshu.gui.algorithm_selection")));
        selection.setPosition(x, y + 22);
        addRenderableWidget(selection);

        var cpuPriorityLabel = Component.translatable("ae2lt.tianshu.gui.cpu_priority");
        var priority = new TabButton(
                Icon.PRIORITY,
                cpuPriorityLabel,
                ignored -> PacketDistributor.sendToServer(
                        SwitchGuisPacket.openSubMenu(PriorityMenu.TYPE)));
        priority.setTooltip(Tooltip.create(cpuPriorityLabel));
        priority.setSize(20, 20);
        priority.setPosition(leftPos + 185, topPos - 5);
        addRenderableWidget(priority);
    }

    private void sendAction(TianshuControllerActionPacket.Action action) {
        PacketDistributor.sendToServer(
                new TianshuControllerActionPacket(menu.token(), menu.getBlockPos(), action));
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
