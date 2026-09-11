package com.moakiee.ae2lt.client;

import appeng.api.config.ActionItems;
import appeng.api.config.FuzzyMode;
import appeng.api.config.CopyMode;
import appeng.api.config.Settings;
import appeng.client.gui.Icon;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.IconButton;
import appeng.client.gui.widgets.ToggleButton;
import appeng.client.gui.widgets.AETextField;
import appeng.client.gui.widgets.ActionButton;
import appeng.client.gui.widgets.Scrollbar;
import appeng.client.gui.widgets.SettingToggleButton;
import appeng.client.gui.widgets.TabButton;
import appeng.core.AEConfig;
import appeng.core.localization.GuiText;
import appeng.core.definitions.AEBlocks;
import appeng.core.network.serverbound.InventoryActionPacket;
import appeng.helpers.InventoryAction;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.math.Axis;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWorkPage;
import com.moakiee.ae2lt.menu.Ae2ltSlotSemantics;
import com.moakiee.ae2lt.menu.TianshuCraftingTermMenu;
import com.moakiee.ae2lt.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ClickType;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class TianshuCraftingTermScreen<M extends TianshuCraftingTermMenu> extends TianshuMaintenanceTermScreen<M> {
    private static final Blitter SMITHING_BACKGROUND = Blitter.texture("guis/pattern_modes.png").src(128, 70, 124, 66);
    private static final Blitter STONE_BACKGROUND = Blitter.texture("guis/pattern_modes.png").src(0, 140, 124, 66);
    private static final Blitter STONE_RECIPE = Blitter.texture("guis/pattern_modes.png").src(124, 140, 20, 22);
    private static final Blitter STONE_RECIPE_SELECTED = STONE_RECIPE.copy().src(124, 162, 20, 22);
    private static final Blitter STONE_RECIPE_HOVER = STONE_RECIPE.copy().src(124, 184, 20, 22);
    private static final Blitter WORK_ARROW = Blitter.texture("guis/crafting.png").src(110, 111, 16, 16);
    private static final List<SlotSemantic> WORK_SLOTS = List.of(SlotSemantics.CRAFTING_GRID, SlotSemantics.CRAFTING_RESULT,
            Ae2ltSlotSemantics.TIANSHU_SMITHING, Ae2ltSlotSemantics.TIANSHU_ANVIL,
            Ae2ltSlotSemantics.TIANSHU_STONECUTTING, Ae2ltSlotSemantics.TIANSHU_CELL,
            Ae2ltSlotSemantics.TIANSHU_CELL_UPGRADE, Ae2ltSlotSemantics.TIANSHU_CELL_CONFIG);
    private final List<TabButton> tabs = new ArrayList<>();
    private final List<StoneRecipeButton> stoneButtons = new ArrayList<>();
    private final ActionButton clearGrid;
    private final ActionButton clearToPlayer;
    private final Button previousCell, nextCell, previousUpgrade, nextUpgrade;
    private final SettingToggleButton<FuzzyMode> fuzzy;
    private final ToggleButton keepCellConfig;
    private final Scrollbar stoneScrollbar;
    private final AETextField anvilName;
    private final TianshuAnvilCostView anvilCostView;
    private ItemStack observedAnvilInput = ItemStack.EMPTY;
    private ItemStack observedStoneInput = ItemStack.EMPTY;

    public TianshuCraftingTermScreen(M menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        anvilCostView = new TianshuAnvilCostView(menu, inventory);
        for (var page : TianshuWorkPage.values()) {
            var icon = switch (page) {
                case CRAFTING -> new ItemStack(Items.CRAFTING_TABLE);
                case SMITHING -> new ItemStack(Items.SMITHING_TABLE);
                case ANVIL -> new ItemStack(ModBlocks.OVERLOAD_ALLOY_ANVIL.get());
                case STONECUTTING -> new ItemStack(Items.STONECUTTER);
                case CELL -> AEBlocks.CELL_WORKBENCH.stack();
            };
            var label = Component.translatable(page.translationKey());
            if (page == TianshuWorkPage.CELL) label.append("\n").append(Component.translatable("ae2lt.tianshu.work.cell_mark_hint"));
            var tabIcon = switch (page) {
                case CRAFTING -> Icon.TAB_CRAFTING;
                case SMITHING -> Icon.TAB_SMITHING;
                case STONECUTTING -> Icon.TAB_STONECUTTING;
                default -> null;
            };
            var button = new WorkPageTabButton(icon, tabIcon, label, b -> menu.setWorkPage(page));
            button.setStyle(TabButton.Style.HORIZONTAL);
            tabs.add(button);
            widgets.add("workTab" + page.ordinal(), button);
        }
        clearGrid = new ActionButton(ActionItems.S_STASH, b -> menu.clearWorkInputs(false));
        clearToPlayer = new ActionButton(ActionItems.S_STASH_TO_PLAYER_INV, b -> menu.clearWorkInputs(true));
        for (var button : List.of(clearGrid, clearToPlayer)) { button.setHalfSize(true); button.setDisableBackground(true); }
        widgets.add("clearCraftingGrid", clearGrid);
        widgets.add("clearToPlayerInv", clearToPlayer);
        previousCell = small(Icon.S_ARROW_UP, () -> menu.setCellConfigRow(menu.cellConfigRow - 1));
        nextCell = small(Icon.S_ARROW_DOWN, () -> menu.setCellConfigRow(menu.cellConfigRow + 1));
        previousUpgrade = small(Icon.S_ARROW_UP, () -> menu.setCellUpgradeRow(menu.cellUpgradeRow - 1));
        nextUpgrade = small(Icon.S_ARROW_DOWN, () -> menu.setCellUpgradeRow(menu.cellUpgradeRow + 1));
        widgets.add("previousCell", previousCell);
        widgets.add("nextCell", nextCell);
        widgets.add("previousUpgrade", previousUpgrade);
        widgets.add("nextUpgrade", nextUpgrade);
        for (var button : List.of(previousCell, nextCell))
            button.setMessage(Component.translatable("ae2lt.tianshu.work.scroll_marks"));
        for (var button : List.of(previousUpgrade, nextUpgrade))
            button.setMessage(Component.translatable("ae2lt.tianshu.work.scroll_upgrades"));
        fuzzy = new SettingToggleButton<>(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL,
                (button, backwards) -> menu.setCellFuzzyMode(button.getNextValue(backwards)));
        fuzzy.setDisableBackground(true);
        widgets.add("cellFuzzy", fuzzy);
        keepCellConfig = new ToggleButton(Icon.COPY_MODE_ON, Icon.COPY_MODE_OFF,
                clear -> menu.setCellCopyMode(clear ? CopyMode.CLEAR_ON_REMOVE : CopyMode.KEEP_ON_REMOVE));
        keepCellConfig.setTooltipOn(List.of(GuiText.CopyMode.text(), GuiText.CopyModeDesc.text(),
                Component.translatable("ae2lt.tianshu.work.cell_copy_clear")));
        keepCellConfig.setTooltipOff(List.of(GuiText.CopyMode.text(), GuiText.CopyModeDesc.text(),
                Component.translatable("ae2lt.tianshu.work.cell_copy_keep")));
        keepCellConfig.setDisableBackground(true);
        widgets.add("cellKeepConfig", keepCellConfig);
        anvilName = new AETextField(style, font, 0, 0, 128, 12);
        anvilName.setBordered(false);
        anvilName.setTooltipMessage(List.of(Component.translatable("ae2lt.tianshu.work.anvil_name")));
        anvilName.setMaxLength(50);
        anvilName.setResponder(menu::setAnvilName);
        widgets.add("anvilName", anvilName);
        stoneScrollbar = widgets.addScrollBar("stonecuttingPatternModeScrollbar", Scrollbar.SMALL);
        stoneScrollbar.setCaptureMouseWheel(false);
        for (int i = 0; i < 8; i++) {
            var button = new StoneRecipeButton(i);
            stoneButtons.add(button);
            widgets.add("stoneRecipe" + i, button);
        }
    }

    private Button small(Icon icon, Runnable action) {
        var button = new IconButton(b -> action.run()) {
            @Override protected Icon getIcon() { return icon; }
        };
        button.setHalfSize(true);
        button.setDisableBackground(true);
        return button;
    }
    private int stoneColumns() { return menu.hasCompactWorkArea() ? 3 : 4; }

    @Override protected void slotClicked(Slot slot, int index, int mouseButton, ClickType type) {
        if (menu.isWorkResult(slot)) {
            // Match AEBaseScreen's CraftingTermSlot gestures; vanilla menus still own actual consumption.
            var action = hasShiftDown() ? InventoryAction.CRAFT_SHIFT
                    : InputConstants.isKeyDown(getMinecraft().getWindow().getWindow(), GLFW.GLFW_KEY_SPACE)
                    ? InventoryAction.CRAFT_ALL : mouseButton == 1 ? InventoryAction.CRAFT_STACK : InventoryAction.CRAFT_ITEM;
            PacketDistributor.sendToServer(new InventoryActionPacket(action, index, 0));
        } else super.slotClicked(slot, index, mouseButton, type);
    }

    @Override public List<Rect2i> getExclusionZones() {
        var zones = super.getExclusionZones();
        for (var tab : tabs) {
            if (tab.visible) zones.add(new Rect2i(tab.getX(), tab.getY(), tab.getWidth(), tab.getHeight()));
        }
        return zones;
    }

    @Override protected void updateBeforeRender() {
        super.updateBeforeRender();
        menu.updateSlotAccess();
        setSlotsHidden(Ae2ltSlotSemantics.TIANSHU_GLOBAL_RESERVE_MARK, true);
        var page = menu.workPage;
        boolean compact = menu.hasCompactWorkArea();
        setTextContent("crafting_grid_title", Component.translatable(page == TianshuWorkPage.CRAFTING
                ? "gui.ae2.CraftingTerminal" : page.translationKey()));
        setSlotsHidden(SlotSemantics.CRAFTING_GRID, page != TianshuWorkPage.CRAFTING);
        setSlotsHidden(SlotSemantics.CRAFTING_RESULT, page != TianshuWorkPage.CRAFTING);
        setSlotsHidden(Ae2ltSlotSemantics.TIANSHU_SMITHING, page != TianshuWorkPage.SMITHING);
        setSlotsHidden(Ae2ltSlotSemantics.TIANSHU_ANVIL, page != TianshuWorkPage.ANVIL);
        setSlotsHidden(Ae2ltSlotSemantics.TIANSHU_STONECUTTING, page != TianshuWorkPage.STONECUTTING);
        for (var semantic : List.of(Ae2ltSlotSemantics.TIANSHU_CELL, Ae2ltSlotSemantics.TIANSHU_CELL_CONFIG, Ae2ltSlotSemantics.TIANSHU_CELL_UPGRADE)) {
            setSlotsHidden(semantic, page != TianshuWorkPage.CELL);
        }
        if (page == TianshuWorkPage.SMITHING) positionRow(Ae2ltSlotSemantics.TIANSHU_SMITHING,
                compact ? new int[]{96, 114, 132, 165} : new int[]{15, 33, 51, 109}, imageHeight - (compact ? 142 : 140));
        if (page == TianshuWorkPage.ANVIL) positionRow(Ae2ltSlotSemantics.TIANSHU_ANVIL,
                compact ? new int[]{96, 124, 165} : new int[]{27, 76, 134}, imageHeight - (compact ? 141 : 133));
        if (page == TianshuWorkPage.STONECUTTING) {
            positionRow(Ae2ltSlotSemantics.TIANSHU_STONECUTTING,
                    compact ? new int[]{167, 167} : new int[]{15, 147}, imageHeight - (compact ? 159 : 140));
            if (compact) menu.getSlots(Ae2ltSlotSemantics.TIANSHU_STONECUTTING).getLast().y = imageHeight - 119;
        }
        if (page == TianshuWorkPage.CELL) {
            positionRow(Ae2ltSlotSemantics.TIANSHU_CELL, new int[]{compact ? 92 : 12}, imageHeight - 161);
            var marks = menu.getSlots(Ae2ltSlotSemantics.TIANSHU_CELL_CONFIG);
            for (int i = 0; i < marks.size(); i++) {
                var slot = marks.get(i);
                boolean visible = menu.isCellMarkVisible(i);
                slot.x = visible ? (compact ? 112 : 42) + (i % 3) * 18 : -10000;
                slot.y = visible ? imageHeight - 161 + (i / 3 - menu.cellConfigRow) * 18 : -10000;
            }
            var upgrades = menu.getSlots(Ae2ltSlotSemantics.TIANSHU_CELL_UPGRADE);
            for (int i = 0; i < upgrades.size(); i++) {
                boolean visible = menu.isCellUpgradeVisible(i);
                upgrades.get(i).x = visible ? (compact ? 168 : 119) : -10000;
                upgrades.get(i).y = visible ? imageHeight - 161 + (i - menu.cellUpgradeRow) * 18 : -10000;
            }
        }
        for (int i = 0; i < tabs.size(); i++) tabs.get(i).setSelected(i == page.ordinal());
        clearGrid.visible = clearToPlayer.visible = page != TianshuWorkPage.CELL;
        if (page == TianshuWorkPage.CRAFTING) {
            var bounds = new Rect2i(leftPos, topPos, imageWidth, imageHeight);
            var clearPosition = getStyle().getWidget("clearCraftingGrid").resolve(bounds);
            var playerPosition = getStyle().getWidget("clearToPlayerInv").resolve(bounds);
            clearGrid.setPosition(clearPosition.getX(), clearPosition.getY());
            clearToPlayer.setPosition(playerPosition.getX(), playerPosition.getY());
        } else {
            int clearX = leftPos + (compact ? (page == TianshuWorkPage.STONECUTTING ? 145 : 164)
                    : (page == TianshuWorkPage.STONECUTTING ? 105 : 137));
            int clearY = topPos + imageHeight - (page == TianshuWorkPage.ANVIL ? (compact ? 150 : 142)
                    : page == TianshuWorkPage.STONECUTTING ? (compact ? 169 : 164) : 162);
            clearGrid.setPosition(clearX, clearY);
            clearToPlayer.setPosition(clearX + 10, clearY);
        }
        previousCell.visible = nextCell.visible = page == TianshuWorkPage.CELL && menu.getMaxCellConfigRow() > 0;
        previousCell.active = menu.cellConfigRow > 0;
        nextCell.active = menu.cellConfigRow < menu.getMaxCellConfigRow();
        previousUpgrade.visible = nextUpgrade.visible = page == TianshuWorkPage.CELL && menu.getMaxCellUpgradeRow() > 0;
        previousUpgrade.active = menu.cellUpgradeRow > 0;
        nextUpgrade.active = menu.cellUpgradeRow < menu.getMaxCellUpgradeRow();
        fuzzy.setVisibility(page == TianshuWorkPage.CELL);
        fuzzy.active = !menu.getCell().isEmpty();
        fuzzy.set(menu.cellFuzzyMode);
        keepCellConfig.setVisibility(page == TianshuWorkPage.CELL);
        keepCellConfig.setState(menu.cellCopyMode == CopyMode.CLEAR_ON_REMOVE);
        anvilName.visible = page == TianshuWorkPage.ANVIL;
        anvilName.active = !menu.getAnvilInput().isEmpty();
        // Keep the same native text-field palette as search; active still gates an empty anvil's editor.
        if (!anvilName.active) anvilName.setFocused(false);
        boolean inputChanged = !ItemStack.matches(observedAnvilInput, menu.getAnvilInput());
        if (inputChanged) observedAnvilInput = menu.getAnvilInput().copy();
        if ((inputChanged || !anvilName.isFocused()) && !anvilName.getValue().equals(menu.anvilItemName)) {
            // Synchronizing a reopened editor must not send the old input name back over a saved rename.
            anvilName.setResponder(value -> {});
            anvilName.setValue(menu.anvilItemName);
            anvilName.setResponder(menu::setAnvilName);
        }
        var stoneInput = menu.getStonecutter().getSlot(0).getItem();
        if (!ItemStack.isSameItemSameComponents(observedStoneInput, stoneInput)) {
            observedStoneInput = stoneInput.copy(); stoneScrollbar.setCurrentScroll(0);
        }
        stoneScrollbar.setRange(0, (menu.getStonecutter().getNumRecipes() + stoneColumns() - 1) / stoneColumns() - 2, 2);
        stoneScrollbar.setVisible(page == TianshuWorkPage.STONECUTTING);
        for (var button : stoneButtons) {
            button.visible = page == TianshuWorkPage.STONECUTTING && button.index < stoneColumns() * 2
                    && button.recipeIndex() < menu.getStonecutter().getNumRecipes();
            if (button.visible) button.setTooltip(Tooltip.create(button.result().getHoverName()));
        }
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (menu.workPage == TianshuWorkPage.CELL && vertical != 0) {
            int delta = -(int) Math.signum(vertical);
            boolean compact = menu.hasCompactWorkArea();
            if (new Rect2i(leftPos + (compact ? 111 : 41), topPos + imageHeight - 162, 54, 54).contains((int) x, (int) y)) {
                menu.setCellConfigRow(menu.cellConfigRow + delta);
                return true;
            }
            if (new Rect2i(leftPos + (compact ? 167 : 118), topPos + imageHeight - 162, 18, 54).contains((int) x, (int) y)) {
                menu.setCellUpgradeRow(menu.cellUpgradeRow + delta);
                return true;
            }
        }
        if (menu.workPage == TianshuWorkPage.STONECUTTING && vertical != 0) {
            var recipes = menu.hasCompactWorkArea()
                    ? new Rect2i(leftPos + 93, topPos + imageHeight - 160, 72, 48)
                    : new Rect2i(leftPos + 33, topPos + imageHeight - 155, 93, 46);
            if (recipes.contains((int) x, (int) y)) {
                stoneScrollbar.setCurrentScroll(stoneScrollbar.getCurrentScroll() - (int) Math.signum(vertical));
                return true;
            }
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    private void positionRow(SlotSemantic semantic, int[] xs, int y) {
        var slots = menu.getSlots(semantic);
        for (int i = 0; i < slots.size(); i++) { slots.get(i).x = xs[i]; slots.get(i).y = y; }
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (handleMaintenanceClick(button)) return true;
        if (menu.workPage == TianshuWorkPage.CELL && hasControlDown()
                && button == org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_MIDDLE
                && getSlotUnderMouse() instanceof appeng.client.gui.me.common.RepoSlot repoSlot) {
            var entry = repoSlot.getEntry();
            if (entry != null && entry.getWhat() != null) {
                var mark = appeng.api.stacks.GenericStack.wrapInItemStack(entry.getWhat(), 1);
                for (var slot : menu.getSlots(Ae2ltSlotSemantics.TIANSHU_CELL_CONFIG)) {
                    if (slot instanceof appeng.menu.slot.FakeSlot fake && fake.isSlotEnabled()
                            && fake.getItem().isEmpty() && fake.canSetFilterTo(mark)) {
                        fake.setFilterTo(mark);
                        break;
                    }
                }
            }
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override public void drawBG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
        super.drawBG(graphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        // The crafting page uses the inherited AE2/WT texture, arrow, result frame and button positions verbatim.
        if (menu.workPage == TianshuWorkPage.CRAFTING) return;
        drawWorkAreaBackground(graphics, offsetX, offsetY);
        boolean compact = menu.hasCompactWorkArea();
        if (menu.workPage == TianshuWorkPage.SMITHING && !compact) {
            SMITHING_BACKGROUND.dest(offsetX + 8, offsetY + imageHeight - 165).blit(graphics);
            return;
        }
        if (menu.workPage == TianshuWorkPage.STONECUTTING && !compact) {
            STONE_BACKGROUND.dest(offsetX + 8, offsetY + imageHeight - 165).blit(graphics);
        }
        if (compact && menu.workPage == TianshuWorkPage.STONECUTTING) {
            drawWorkAreaFrame(graphics, offsetX + 93, offsetY + imageHeight - 160, 72, 48);
            graphics.pose().pushPose();
            graphics.pose().translate(offsetX + 183, offsetY + imageHeight - 139, 0);
            graphics.pose().mulPose(Axis.ZP.rotationDegrees(90));
            WORK_ARROW.dest(0, 0).blit(graphics);
            graphics.pose().popPose();
        }
        if (menu.workPage == TianshuWorkPage.CELL) {
            drawCellSlot(graphics, offsetX + (compact ? 92 : 12), offsetY + imageHeight - 143, fuzzy.active);
            drawWorkSlot(graphics, offsetX + (compact ? 92 : 12), offsetY + imageHeight - 125);
            for (int i = 0; i < 9; i++) drawCellSlot(graphics, offsetX + (compact ? 112 : 42) + i % 3 * 18,
                    offsetY + imageHeight - 161 + i / 3 * 18, menu.isCellMarkVisible(menu.cellConfigRow * 3 + i));
            for (int i = 0; i < 3; i++) drawCellSlot(graphics, offsetX + (compact ? 168 : 119),
                    offsetY + imageHeight - 161 + i * 18, menu.isCellUpgradeVisible(menu.cellUpgradeRow + i));
        }
        for (var semantic : WORK_SLOTS) {
            if (semantic == Ae2ltSlotSemantics.TIANSHU_CELL_CONFIG || semantic == Ae2ltSlotSemantics.TIANSHU_CELL_UPGRADE) continue;
            for (var slot : menu.getSlots(semantic)) {
                if (slot.x < 0 || slot.y < 0 || !slot.isActive()) continue;
                drawWorkSlot(graphics, offsetX + slot.x, offsetY + slot.y);
            }
        }
        if (compact && menu.workPage == TianshuWorkPage.SMITHING) {
            // Keep the native template/base/addition empty-slot hints at full icon size.
            Blitter.texture("guis/pattern_modes.png").src(134, 94, 54, 18)
                    .dest(offsetX + 95, offsetY + imageHeight - 143).blit(graphics);
            WORK_ARROW.dest(offsetX + 149, offsetY + imageHeight - 142).blit(graphics);
        } else if (menu.workPage == TianshuWorkPage.ANVIL) {
            WORK_ARROW.dest(offsetX + (compact ? 146 : 106), offsetY + imageHeight - (compact ? 141 : 133)).blit(graphics);
        }
    }

    protected void drawWorkAreaBackground(GuiGraphics graphics, int offsetX, int offsetY) {
        drawWorkAreaFrame(graphics, offsetX + 7, offsetY + imageHeight - 166, 162, 68);
    }

    protected final void drawWorkAreaFrame(GuiGraphics graphics, int x, int y, int width, int height) {
        // Reuse the terminal's frame and panel texture, including changes supplied by resource packs.
        var texture = Blitter.texture("guis/crafting.png");
        texture.src(9, 88, 1, 1).dest(x + 1, y + 1, width - 2, height - 2).blit(graphics);
        texture.src(7, 85, 162, 1).dest(x, y, width, 1).blit(graphics);
        texture.src(7, 152, 162, 1).dest(x, y + height - 1, width, 1).blit(graphics);
        texture.src(7, 86, 1, 66).dest(x, y + 1, 1, height - 2).blit(graphics);
        texture.src(168, 86, 1, 66).dest(x + width - 1, y + 1, 1, height - 2).blit(graphics);
    }

    private void drawWorkSlot(GuiGraphics g, int x, int y) {
        Blitter.texture("guis/crafting.png").src(25, 92, 18, 18).dest(x - 1, y - 1).blit(g);
    }

    private void drawCellSlot(GuiGraphics graphics, int x, int y, boolean enabled) {
        // Same optional-slot texture and disabled opacity used by AE2's cell workbench.
        Icon.SLOT_BACKGROUND.getBlitter().dest(x - 1, y - 1).opacity(enabled ? 1.0f : 0.2f).blit(graphics);
    }

    @Override public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(graphics, offsetX, offsetY, mouseX, mouseY);
        boolean compact = menu.hasCompactWorkArea();
        if (menu.workPage == TianshuWorkPage.CELL && menu.getMaxCellConfigRow() > 0) {
            graphics.drawString(font, (menu.cellConfigRow + 1) + "–" + Math.min(menu.cellConfigRow + 3, menu.getCellConfigRows()), compact ? 126 : 56,
                    imageHeight - 108, 0x413f54, false);
        } else if (menu.workPage == TianshuWorkPage.ANVIL) {
            graphics.drawString(font, "+", compact ? 115 : 58, imageHeight - (compact ? 137 : 129), 0x413f54, false);
            anvilCostView.renderCost(graphics, compact ? 92 : 8, imageHeight - 109, compact ? 93 : 160, mouseX, mouseY);
        }
    }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (anvilName.visible && anvilName.active && anvilName.isFocused() && key != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            return anvilName.keyPressed(key, scanCode, modifiers) || anvilName.canConsumeInput();
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override public boolean charTyped(char character, int modifiers) {
        // MEStorageScreen consumes spaces while its search is empty; anvil names have their own editor.
        if (anvilName.visible && anvilName.active && anvilName.isFocused()) return anvilName.charTyped(character, modifiers);
        return super.charTyped(character, modifiers);
    }

    @Override public void onClose() {
        if (AEConfig.instance().isClearGridOnClose()) {
            // AE2's close preference applies to its original 3x3 grid, even while another page is shown.
            menu.setWorkPage(TianshuWorkPage.CRAFTING);
            menu.clearCraftingGrid();
        }
        super.onClose();
    }

    private final class StoneRecipeButton extends Button {
        private final int index;
        StoneRecipeButton(int index) {
            super(0, 0, 20, 22, Component.empty(), b -> menu.selectStoneRecipe(stoneScrollbar.getCurrentScroll() * stoneColumns() + index), DEFAULT_NARRATION);
            this.index = index;
        }
        int recipeIndex() { return stoneScrollbar.getCurrentScroll() * stoneColumns() + index; }
        ItemStack result() {
            return menu.getStonecutter().getRecipes().get(recipeIndex()).value().getResultItem(menu.getPlayer().registryAccess());
        }
        @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (recipeIndex() < menu.getStonecutter().getNumRecipes()) {
                boolean selected = recipeIndex() == menu.stoneRecipe;
                var background = selected ? STONE_RECIPE_SELECTED : isHovered() ? STONE_RECIPE_HOVER : STONE_RECIPE;
                background.dest(getX(), getY()).blit(graphics);
                int y = getY() + (selected || isHovered() ? 3 : 2);
                graphics.renderItem(result(), getX() + 2, y);
                graphics.renderItemDecorations(font, result(), getX() + 2, y);
            }
        }
    }

    /** Compact native tabs beside the tool area, with their bottom aligned above the player inventory. */
    private static final class WorkPageTabButton extends TabButton {
        private final ItemStack itemIcon;
        private final Icon nativeIcon;

        WorkPageTabButton(ItemStack itemIcon, Icon nativeIcon, Component label, OnPress onPress) {
            super(itemIcon, label, onPress);
            this.itemIcon = itemIcon;
            this.nativeIcon = nativeIcon;
        }

        @Override public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            var background = isFocused() ? Icon.HORIZONTAL_TAB_FOCUS
                    : isSelected() ? Icon.HORIZONTAL_TAB_SELECTED : Icon.HORIZONTAL_TAB;
            background.getBlitter().dest(getX(), getY(), getWidth(), getHeight()).blit(graphics);
            int iconY = getY() + (getHeight() - 16) / 2;
            if (nativeIcon != null) {
                nativeIcon.getBlitter().dest(getX() + 1, iconY).blit(graphics);
            } else {
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, 100);
                graphics.renderItem(itemIcon, getX() + 1, iconY);
                graphics.pose().popPose();
            }
        }
    }
}
