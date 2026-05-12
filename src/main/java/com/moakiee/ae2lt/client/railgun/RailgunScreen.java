package com.moakiee.ae2lt.client.railgun;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import com.moakiee.ae2lt.item.railgun.RailgunModuleType;
import com.moakiee.ae2lt.item.railgun.RailgunSettings;
import com.moakiee.ae2lt.logic.railgun.RailgunBinding;
import com.moakiee.ae2lt.menu.railgun.RailgunMenu;
import com.moakiee.ae2lt.network.railgun.RailgunSettingsTogglePacket;

/**
 * Vanilla-style container screen for the railgun module GUI.
 *
 * <p>Layout overview (imageWidth=176, imageHeight=189):
 * <pre>
 *   y=6:   [Item title from menu]
 *   y=14:  [Modules]   [Compute x2]                 [Tactical]
 *   y=24:  [CORE][lbl] [COMP][COMP]                 [TerrainBtn 116-172]
 *   y=42:                  [Accel x2]
 *   y=50:  [ENRG][lbl] [ACCEL][ACCEL]               [PvpBtn 116-172]
 *   y=95:  [Inventory]                              [Network: Bound/Unbound]
 *   y=107: [player inv]
 *   y=165: [hotbar]
 * </pre>
 */
public class RailgunScreen extends AbstractContainerScreen<RailgunMenu> {

    // Toggle buttons (56x16 wide bars, label + state inside)
    private static final int BTN_X = 116;
    private static final int BTN_TERRAIN_Y = 24;
    private static final int BTN_PVP_Y = 50;
    private static final int BTN_W = 56;
    private static final int BTN_H = 16;

    // Section / slot labels
    private static final int LBL_MODULES_X = 8, LBL_MODULES_Y = 14;
    private static final int LBL_COMPUTE_ROW_X = 78, LBL_COMPUTE_ROW_Y = 14;
    private static final int LBL_ACCEL_ROW_X = 78, LBL_ACCEL_ROW_Y = 42;
    private static final int LBL_TACTICAL_X = 118, LBL_TACTICAL_Y = 14;

    private static final int LBL_CORE_X = 44, LBL_CORE_Y = 28;
    private static final int LBL_ENERGY_X = 44, LBL_ENERGY_Y = 54;
    private static final int LBL_OVERLOAD_EXEC_X = 44, LBL_OVERLOAD_EXEC_Y = 80;

    // Network status (right of inventory title row at y=95)
    private static final int LBL_NETWORK_X = 88, LBL_NETWORK_Y = 95;
    // Cached width of the displayed network status text, for hover hit-testing
    private int networkLabelWidth = 0;

    private static final int COLOR_LABEL = 0xFF404040;
    private static final int COLOR_LABEL_DIM = 0xFF808080;
    private static final int COLOR_BTN_ON_TEXT = 0xFFFFFFFF;
    private static final int COLOR_BTN_OFF_TEXT = 0xFFCCCCCC;

    public RailgunScreen(RailgunMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 207;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        // Outer panel
        gfx.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xFFC6C6C6);
        gfx.fill(x + 2, y + 2, x + this.imageWidth - 2, y + this.imageHeight - 2, 0xFFD8D8D8);

        // Module slot tiles (positions match RailgunMenu)
        // Left column: CORE(26,24), ENERGY(26,50)
        drawSlotTile(gfx, x + 26, y + 24);
        drawSlotTile(gfx, x + 26, y + 50);
        // Right area: COMPUTE(80,24)(98,24), ACCEL(80,50)(98,50)
        drawSlotTile(gfx, x + 80, y + 24);
        drawSlotTile(gfx, x + 98, y + 24);
        drawSlotTile(gfx, x + 80, y + 50);
        drawSlotTile(gfx, x + 98, y + 50);
        // Overload Execution slot (26,76)
        drawSlotTile(gfx, x + 26, y + 76);

        // Toggle buttons (background only — label drawn in renderLabels for proper z-order)
        var settings = menu.host().getSettings();
        drawToggleBg(gfx, x + BTN_X, y + BTN_TERRAIN_Y, settings.terrainDestruction(), 0xFFCC4444);
        drawToggleBg(gfx, x + BTN_X, y + BTN_PVP_Y, settings.pvpLock(), 0xFF4488CC);

        // Player inventory backing
        int invX = x + 8, invY = y + 125;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlotTile(gfx, invX + col * 18, invY + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlotTile(gfx, invX + col * 18, invY + 58);
        }
    }

    private static void drawSlotTile(GuiGraphics gfx, int x, int y) {
        gfx.fill(x - 1, y - 1, x + 17, y + 17, 0xFF373737);
        gfx.fill(x, y, x + 16, y + 16, 0xFF8B8B8B);
    }

    private static void drawToggleBg(GuiGraphics gfx, int x, int y, boolean on, int onColor) {
        int border = on ? onColor : 0xFF555555;
        int fill = on ? darken(onColor) : 0xFF2A2A2A;
        gfx.fill(x - 1, y - 1, x + BTN_W + 1, y + BTN_H + 1, border);
        gfx.fill(x, y, x + BTN_W, y + BTN_H, fill);
    }

    private static int darken(int argb) {
        int a = argb >>> 24;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        r = (int) (r * 0.55);
        g = (int) (g * 0.55);
        b = (int) (b * 0.55);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @Override
    protected void renderLabels(GuiGraphics gfx, int mouseX, int mouseY) {
        // Default labels: container title at (titleLabelX, titleLabelY) + inventory title
        super.renderLabels(gfx, mouseX, mouseY);

        // Section headers
        gfx.drawString(this.font,
                Component.translatable("ae2lt.railgun.gui.section.modules"),
                LBL_MODULES_X, LBL_MODULES_Y, COLOR_LABEL, false);
        gfx.drawString(this.font,
                Component.translatable("ae2lt.railgun.gui.section.compute_row"),
                LBL_COMPUTE_ROW_X, LBL_COMPUTE_ROW_Y, COLOR_LABEL_DIM, false);
        gfx.drawString(this.font,
                Component.translatable("ae2lt.railgun.gui.section.accel_row"),
                LBL_ACCEL_ROW_X, LBL_ACCEL_ROW_Y, COLOR_LABEL_DIM, false);
        gfx.drawString(this.font,
                Component.translatable("ae2lt.railgun.gui.section.tactical"),
                LBL_TACTICAL_X, LBL_TACTICAL_Y, COLOR_LABEL, false);

        // Slot adjacent labels (left column)
        gfx.drawString(this.font,
                Component.translatable("ae2lt.railgun.gui.slot.core"),
                LBL_CORE_X, LBL_CORE_Y, COLOR_LABEL, false);
        gfx.drawString(this.font,
                Component.translatable("ae2lt.railgun.gui.slot.energy"),
                LBL_ENERGY_X, LBL_ENERGY_Y, COLOR_LABEL, false);
        gfx.drawString(this.font,
                Component.translatable("ae2lt.railgun.gui.slot.overload_execution"),
                LBL_OVERLOAD_EXEC_X, LBL_OVERLOAD_EXEC_Y, COLOR_LABEL, false);

        // Toggle button text: "<功能名> <开/关>"
        var settings = menu.host().getSettings();
        drawToggleLabel(gfx, BTN_X, BTN_TERRAIN_Y,
                "ae2lt.railgun.gui.button.terrain", settings.terrainDestruction());
        drawToggleLabel(gfx, BTN_X, BTN_PVP_Y,
                "ae2lt.railgun.gui.button.pvp_lock", settings.pvpLock());

        // Network status line
        Component networkLine = buildNetworkStatus();
        gfx.drawString(this.font, networkLine, LBL_NETWORK_X, LBL_NETWORK_Y, COLOR_LABEL, false);
        this.networkLabelWidth = this.font.width(networkLine);
    }

    private void drawToggleLabel(GuiGraphics gfx, int relX, int relY, String labelKey, boolean on) {
        Component label = Component.translatable(labelKey);
        Component state = on
                ? Component.translatable("ae2lt.railgun.gui.state.on").withStyle(ChatFormatting.GREEN)
                : Component.translatable("ae2lt.railgun.gui.state.off").withStyle(ChatFormatting.RED);
        Component combined = Component.empty().append(label).append(" ").append(state);
        int textW = this.font.width(combined);
        int textX = relX + (BTN_W - textW) / 2;
        int textY = relY + 4;
        int textColor = on ? COLOR_BTN_ON_TEXT : COLOR_BTN_OFF_TEXT;
        gfx.drawString(this.font, combined, textX, textY, textColor, false);
    }

    private Component buildNetworkStatus() {
        ItemStack stack = menu.host().getStack();
        GlobalPos pos = RailgunBinding.getBoundPos(stack);
        Component title = Component.translatable("ae2lt.railgun.gui.network.title");
        if (pos == null) {
            return Component.empty()
                    .append(title)
                    .append(": ")
                    .append(Component.translatable("ae2lt.railgun.gui.network.unbound")
                            .withStyle(ChatFormatting.RED));
        }
        return Component.empty()
                .append(title)
                .append(": ")
                .append(Component.translatable("ae2lt.railgun.gui.network.bound")
                        .withStyle(ChatFormatting.GREEN));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int gx = (this.width - this.imageWidth) / 2;
            int gy = (this.height - this.imageHeight) / 2;
            if (inRect(mouseX, mouseY, gx + BTN_X, gy + BTN_TERRAIN_Y, BTN_W, BTN_H)) {
                toggleTerrain();
                playClick();
                return true;
            }
            if (inRect(mouseX, mouseY, gx + BTN_X, gy + BTN_PVP_Y, BTN_W, BTN_H)) {
                togglePvp();
                playClick();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void playClick() {
        if (this.minecraft != null && this.minecraft.getSoundManager() != null) {
            this.minecraft.getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.0F));
        }
    }

    private static boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private void toggleTerrain() {
        RailgunSettings current = menu.host().getSettings();
        RailgunSettings next = current.withTerrain(!current.terrainDestruction());
        menu.host().setSettings(next);
        PacketDistributor.sendToServer(new RailgunSettingsTogglePacket(next));
    }

    private void togglePvp() {
        RailgunSettings current = menu.host().getSettings();
        RailgunSettings next = current.withPvpLock(!current.pvpLock());
        menu.host().setSettings(next);
        PacketDistributor.sendToServer(new RailgunSettingsTogglePacket(next));
    }

    @Override
    protected void renderTooltip(GuiGraphics gfx, int mouseX, int mouseY) {
        super.renderTooltip(gfx, mouseX, mouseY);
        int gx = (this.width - this.imageWidth) / 2;
        int gy = (this.height - this.imageHeight) / 2;

        // Toggle buttons: full multi-line tooltip via existing on/off keys
        if (inRect(mouseX, mouseY, gx + BTN_X, gy + BTN_TERRAIN_Y, BTN_W, BTN_H)) {
            var s = menu.host().getSettings();
            var key = s.terrainDestruction()
                    ? "ae2lt.railgun.terrain.on"
                    : "ae2lt.railgun.terrain.off";
            gfx.renderTooltip(this.font, Component.translatable(key), mouseX, mouseY);
            return;
        }
        if (inRect(mouseX, mouseY, gx + BTN_X, gy + BTN_PVP_Y, BTN_W, BTN_H)) {
            var s = menu.host().getSettings();
            var key = s.pvpLock()
                    ? "ae2lt.railgun.pvp_lock.on"
                    : "ae2lt.railgun.pvp_lock.off";
            gfx.renderTooltip(this.font, Component.translatable(key), mouseX, mouseY);
            return;
        }

        // Empty module slot: show what kind of module fits
        if (this.hoveredSlot != null
                && this.hoveredSlot.container == this.menu.getSlot(0).container
                && !this.hoveredSlot.hasItem()) {
            String tooltipKey = slotTooltipKey(this.hoveredSlot);
            if (tooltipKey != null) {
                List<Component> lines = new ArrayList<>();
                String raw = Component.translatable(tooltipKey).getString();
                for (String line : raw.split("\n")) {
                    lines.add(Component.literal(line));
                }
                gfx.renderComponentTooltip(this.font, lines, mouseX, mouseY);
                return;
            }
        }

        // Network status hover: full pos + dimension
        if (mouseY >= gy + LBL_NETWORK_Y - 1
                && mouseY <= gy + LBL_NETWORK_Y + 9
                && mouseX >= gx + LBL_NETWORK_X
                && mouseX <= gx + LBL_NETWORK_X + this.networkLabelWidth) {
            ItemStack stack = menu.host().getStack();
            GlobalPos pos = RailgunBinding.getBoundPos(stack);
            Component tip;
            if (pos == null) {
                tip = Component.translatable("ae2lt.railgun.gui.network.tooltip.unbound");
            } else {
                tip = Component.translatable("ae2lt.railgun.gui.network.tooltip.bound",
                        pos.pos().getX(), pos.pos().getY(), pos.pos().getZ(),
                        pos.dimension().location().toString());
            }
            List<Component> lines = new ArrayList<>();
            for (String line : tip.getString().split("\n")) {
                lines.add(Component.literal(line));
            }
            gfx.renderComponentTooltip(this.font, lines, mouseX, mouseY);
        }
    }

    /** Returns the empty-slot tooltip key for the given module slot, or null if not a module slot. */
    private static String slotTooltipKey(Slot slot) {
        // Slot indices match RailgunMenu's addSlot order:
        // 0=CORE, 1=ENERGY, 2-3=COMPUTE, 4-5=ACCELERATION
        return switch (slot.index) {
            case 0 -> typeKey(RailgunModuleType.CORE);
            case 1 -> typeKey(RailgunModuleType.ENERGY);
            case 2, 3 -> typeKey(RailgunModuleType.COMPUTE);
            case 4, 5 -> typeKey(RailgunModuleType.ACCELERATION);
            case 6 -> typeKey(RailgunModuleType.OVERLOAD_EXECUTION);
            default -> null;
        };
    }

    private static String typeKey(RailgunModuleType type) {
        return switch (type) {
            case CORE -> "ae2lt.railgun.gui.slot.tooltip.core";
            case ENERGY -> "ae2lt.railgun.gui.slot.tooltip.energy";
            case COMPUTE -> "ae2lt.railgun.gui.slot.tooltip.compute";
            case ACCELERATION -> "ae2lt.railgun.gui.slot.tooltip.acceleration";
            case OVERLOAD_EXECUTION -> "ae2lt.railgun.gui.slot.tooltip.overload_execution";
        };
    }
}
