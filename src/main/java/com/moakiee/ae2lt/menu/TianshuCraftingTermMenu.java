package com.moakiee.ae2lt.menu;

import appeng.api.config.FuzzyMode;
import appeng.api.config.CopyMode;
import appeng.api.config.Settings;
import appeng.api.inventories.ISegmentedInventory;
import appeng.blockentity.misc.CellWorkbenchBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.storage.cells.ICellWorkbenchItem;
import appeng.helpers.InventoryAction;
import appeng.items.storage.ViewCellItem;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.me.common.MEStorageMenu;
import appeng.menu.me.items.CraftingTermMenu;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.FakeSlot;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.CarriedItemInventory;
import appeng.util.inv.PlayerInternalInventory;
import com.moakiee.ae2lt.blockentity.TianshuSupercomputerPortBlockEntity;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuCraftingTerminalHost;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuTerminalTarget;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWorkPage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import org.jetbrains.annotations.Nullable;

/** Five manual workstations. Only the native 3x3 grid belongs to the terminal host. */
public class TianshuCraftingTermMenu extends CraftingTermMenu implements TianshuMaintenanceMenu {
    public static final MenuType<TianshuCraftingTermMenu> TYPE = MenuTypeBuilder
            .create((MenuTypeBuilder.MenuFactory<TianshuCraftingTermMenu, TianshuCraftingTerminalHost>)
                    TianshuCraftingTermMenu::new, TianshuCraftingTerminalHost.class)
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath("ae2lt", "tianshu_crafting_terminal"));
    public static final int CELL_CONFIG_SLOTS = 63;
    public static final int CELL_PAGE_SIZE = 9;

    @GuiSync(150) public TianshuWorkPage workPage = TianshuWorkPage.CRAFTING;
    @GuiSync(151) public int cellConfigRow;
    @GuiSync(152) public int cellConfigSize;
    @GuiSync(153) public int cellUpgradeSize;
    @GuiSync(154) public FuzzyMode cellFuzzyMode = FuzzyMode.IGNORE_ALL;
    @GuiSync(155) public int anvilCost;
    @GuiSync(156) public int stoneRecipe = -1;
    @GuiSync(157) public boolean maintenanceAvailable;
    @GuiSync(158) public boolean maintainableView;
    @GuiSync(159) public int tianshuSelectionRevision;
    @GuiSync(160) public int cellUpgradeRow;
    @GuiSync(161) public CopyMode cellCopyMode = CopyMode.CLEAR_ON_REMOVE;

    protected final TianshuCraftingTerminalHost tianshuHost;
    private final TianshuMaintenanceSession maintenanceSession;
    @Nullable private TianshuTerminalTarget boundTianshuTarget;
    private final FakeSlot globalReserveMarkSlot;
    private final SmithingMenu smithing;
    private final OverloadAlloyAnvilMenu anvil;
    private final StonecutterMenu stonecutter;
    // A session-local native workbench owns its configuration copy rules and card inventory.
    // It is never placed in a world; native saveChanges safely ignores a missing level.
    private final CellWorkbenchBlockEntity cellWorkbench = new CellWorkbenchBlockEntity(
            AEBlocks.CELL_WORKBENCH.block().getBlockEntityType(), BlockPos.ZERO,
            AEBlocks.CELL_WORKBENCH.block().defaultBlockState());
    private final AppEngInternalInventory cellInventory =
            (AppEngInternalInventory) cellWorkbench.getSubInventory(ISegmentedInventory.CELLS);
    private ItemStack configuredCell = ItemStack.EMPTY;
    private final List<Slot> extraInputs = new ArrayList<>();
    private boolean returnedInputs;

    public TianshuCraftingTermMenu(int id, Inventory inventory, TianshuCraftingTerminalHost host) {
        this(TYPE, id, inventory, host);
    }

    protected TianshuCraftingTermMenu(MenuType<?> type, int id, Inventory inventory, TianshuCraftingTerminalHost host) {
        super(type, id, inventory, host, true);
        tianshuHost = host;
        maintenanceSession = new TianshuMaintenanceSession(this, this::resolveBoundTianshu,
                () -> tianshuSelectionRevision, action -> sendClientAction("maintenanceAction", action));
        globalReserveMarkSlot = new FakeSlot(new AppEngInternalInventory(null, 1, 1), 0);
        globalReserveMarkSlot.x = globalReserveMarkSlot.y = -10000;
        addSlot(globalReserveMarkSlot, Ae2ltSlotSemantics.TIANSHU_GLOBAL_RESERVE_MARK);

        // These menus are computation/callback engines, without a packet synchronizer or a world block to clear.
        smithing = new SmithingMenu(id, inventory);
        anvil = new OverloadAlloyAnvilMenu(id, inventory, ContainerLevelAccess.NULL);
        stonecutter = new TianshuStonecutterMenu(id, inventory);
        addWorkSlots(smithing, 3, TianshuWorkPage.SMITHING, Ae2ltSlotSemantics.TIANSHU_SMITHING);
        addWorkSlots(anvil, 2, TianshuWorkPage.ANVIL, Ae2ltSlotSemantics.TIANSHU_ANVIL);
        addWorkSlots(stonecutter, 1, TianshuWorkPage.STONECUTTING, Ae2ltSlotSemantics.TIANSHU_STONECUTTING);

        cellInventory.setMaxStackSize(0, 1);
        var cellSlot = new AppEngSlot(cellInventory, 0) {
            @Override public void setChanged() { super.setChanged(); if (isServerSide()) refreshCell(); }
            @Override public boolean mayPlace(ItemStack stack) {
                return workPage == TianshuWorkPage.CELL && stack.getItem() instanceof ICellWorkbenchItem cell && cell.isEditable(stack);
            }
            @Override public boolean mayPickup(Player player) { return workPage == TianshuWorkPage.CELL; }
        };
        addSlot(cellSlot, Ae2ltSlotSemantics.TIANSHU_CELL);
        extraInputs.add(cellSlot);
        for (int i = 0; i < 8; i++) {
            final int inventoryIndex = i;
            addSlot(new AppEngSlot(cellProxy(true), i) {
                @Override public ItemStack getItem() { return getInventory().getStackInSlot(inventoryIndex); }
                @Override public boolean isSlotEnabled() { return isCellUpgradeVisible(inventoryIndex); }
                @Override public boolean mayPlace(ItemStack stack) { return isSlotEnabled() && super.mayPlace(stack); }
                @Override public boolean mayPickup(Player player) { return isSlotEnabled() && super.mayPickup(player); }
            }, Ae2ltSlotSemantics.TIANSHU_CELL_UPGRADE);
        }
        // Fixed slot identities are essential: a delayed click from page 1 must never write page 2.
        for (int i = 0; i < CELL_CONFIG_SLOTS; i++) {
            final int inventoryIndex = i;
            addSlot(new FakeSlot(cellProxy(false), i) {
                @Override public ItemStack getItem() { return getInventory().getStackInSlot(inventoryIndex); }
                @Override public boolean isSlotEnabled() { return isCellMarkVisible(inventoryIndex); }
                @Override public boolean canSetFilterTo(ItemStack stack) { return isSlotEnabled() && super.canSetFilterTo(stack); }
            }, Ae2ltSlotSemantics.TIANSHU_CELL_CONFIG);
        }
        registerClientAction("workPage", TianshuWorkPage.class, this::setWorkPage);
        registerClientAction("cellConfigRow", Integer.class, this::setCellConfigRow);
        registerClientAction("cellUpgradeRow", Integer.class, this::setCellUpgradeRow);
        registerClientAction("cellFuzzy", FuzzyMode.class, this::setCellFuzzyMode);
        registerClientAction("cellCopyMode", CopyMode.class, this::setCellCopyMode);
        registerClientAction("anvilName", String.class, this::setAnvilName);
        registerClientAction("stoneRecipe", Integer.class, this::selectStoneRecipe);
        registerClientAction("workRecipe", String.class, this::fillWorkRecipe);
        registerClientAction("clearWorkToNetwork", TianshuWorkPage.class, page -> clearWorkInputs(page, false));
        registerClientAction("clearWorkToPlayer", TianshuWorkPage.class, page -> clearWorkInputs(page, true));
        registerClientAction("setMaintainableView", Boolean.class, this::setMaintainableView);
        registerClientAction("maintenanceAction", TianshuMaintenanceSession.MaintenanceAction.class, maintenanceSession::maintenanceActionServer);
        updateSlotAccess();
    }

    private void addWorkSlots(AbstractContainerMenu engine, int inputCount, TianshuWorkPage page, SlotSemantic semantic) {
        for (int i = 0; i <= inputCount; i++) {
            var slot = new TianshuWorkSlot(engine.getSlot(i), () -> workPage == page, i == inputCount);
            addSlot(slot, semantic);
            if (i < inputCount) extraInputs.add(slot);
        }
    }

    private InternalInventory cellProxy(boolean upgrades) {
        return new InternalInventory() {
            private InternalInventory delegate() {
                return upgrades ? cellWorkbench.getUpgrades() : cellWorkbench.getConfig().createMenuWrapper();
            }
            @Override public int size() { return upgrades ? 8 : CELL_CONFIG_SLOTS; }
            @Override public int getSlotLimit(int slot) { var inv = delegate(); return slot < inv.size() ? inv.getSlotLimit(slot) : 0; }
            @Override public ItemStack getStackInSlot(int slot) { var inv = delegate(); return slot < inv.size() ? inv.getStackInSlot(slot) : ItemStack.EMPTY; }
            @Override public void setItemDirect(int slot, ItemStack stack) {
                var inv = delegate();
                if (slot < inv.size()) inv.setItemDirect(slot, stack);
            }
            @Override public boolean isItemValid(int slot, ItemStack stack) {
                var inv = delegate(); return slot < inv.size() && inv.isItemValid(slot, stack);
            }
        };
    }

    public ItemStack getCell() { return cellInventory.getStackInSlot(0); }
    public int getCellConfigRows() { return (cellConfigSize + 2) / 3; }
    public int getMaxCellConfigRow() { return Math.max(0, getCellConfigRows() - 3); }
    public int getMaxCellUpgradeRow() { return Math.max(0, cellUpgradeSize - 3); }
    public boolean isCellMarkVisible(int index) {
        return workPage == TianshuWorkPage.CELL && index >= cellConfigRow * 3
                && index < Math.min(cellConfigSize, cellConfigRow * 3 + CELL_PAGE_SIZE);
    }
    public boolean isCellUpgradeVisible(int index) {
        return workPage == TianshuWorkPage.CELL && index >= cellUpgradeRow
                && index < Math.min(cellUpgradeSize, cellUpgradeRow + 3);
    }
    public boolean hasCompactWorkArea() { return false; }
    public StonecutterMenu getStonecutter() { return stonecutter; }
    public OverloadAlloyAnvilMenu getAnvil() { return anvil; }
    public ItemStack getAnvilInput() { return anvil.getSlot(0).getItem(); }

    public void setWorkPage(TianshuWorkPage page) {
        if (isClientSide()) { sendClientAction("workPage", page); return; }
        if (!isMainWorkPage()) return;
        workPage = page;
        updateSlotAccess();
        broadcastChanges();
    }

    public void setCellConfigRow(int row) {
        if (isClientSide()) { sendClientAction("cellConfigRow", row); return; }
        if (!isMainWorkPage() || workPage != TianshuWorkPage.CELL) return;
        refreshCell();
        cellConfigRow = Math.clamp(row, 0, getMaxCellConfigRow());
        broadcastChanges();
    }

    public void setCellUpgradeRow(int row) {
        if (isClientSide()) { sendClientAction("cellUpgradeRow", row); return; }
        if (!isMainWorkPage() || workPage != TianshuWorkPage.CELL) return;
        refreshCell();
        cellUpgradeRow = Math.clamp(row, 0, getMaxCellUpgradeRow());
        broadcastChanges();
    }

    public void setCellCopyMode(CopyMode mode) {
        if (isClientSide()) { sendClientAction("cellCopyMode", mode); return; }
        if (!isMainWorkPage() || workPage != TianshuWorkPage.CELL) return;
        cellWorkbench.getConfigManager().putSetting(Settings.COPY_MODE, mode);
        broadcastChanges();
    }

    public void setCellFuzzyMode(FuzzyMode mode) {
        if (isClientSide()) { sendClientAction("cellFuzzy", mode); return; }
        if (isMainWorkPage() && workPage == TianshuWorkPage.CELL && getCell().getItem() instanceof ICellWorkbenchItem cell) {
            cell.setFuzzyMode(getCell(), mode);
        }
    }

    public void setAnvilName(String name) {
        if (name.length() > 50) return;
        if (isClientSide()) { sendClientAction("anvilName", name); return; }
        if (isMainWorkPage() && workPage == TianshuWorkPage.ANVIL) anvil.setItemName(name);
    }

    public void selectStoneRecipe(int index) {
        if (isClientSide()) { sendClientAction("stoneRecipe", index); return; }
        if (isMainWorkPage() && workPage == TianshuWorkPage.STONECUTTING) stonecutter.clickMenuButton(getPlayer(), index);
    }

    protected boolean isMainWorkPage() { return true; }
    public boolean canUseWorkstations() { return isMainWorkPage(); }

    @Override public InternalInventory getCraftingMatrix() {
        // Also gates AE2's recipe-transfer packet, which accesses this inventory without clicking a slot.
        return isMainWorkPage() && workPage == TianshuWorkPage.CRAFTING ? super.getCraftingMatrix() : InternalInventory.empty();
    }

    public void prepareCraftingTransfer() { setWorkPage(TianshuWorkPage.CRAFTING); }

    @Nullable private AbstractContainerMenu workEngine() {
        return switch (workPage) {
            case SMITHING -> smithing;
            case ANVIL -> anvil;
            case STONECUTTING -> stonecutter;
            default -> null;
        };
    }

    private int workInputCount() {
        return switch (workPage) {
            case SMITHING -> 3;
            case ANVIL -> 2;
            case STONECUTTING -> 1;
            default -> 0;
        };
    }

    public boolean isWorkResult(@Nullable Slot slot) {
        return slot instanceof TianshuWorkSlot work && work.result;
    }

    /** The same four gestures and destination inventories as AE2's CraftingTermSlot.doClick. */
    private void craftWorkResult(TianshuWorkSlot result, InventoryAction action) {
        if (isClientSide() || !isMainWorkPage() || !allowsSlot(result.index)
                || !isValidMenu() || !stillValid(getPlayer())) return;
        var engine = workEngine();
        if (engine == null || !result.hasItem() || !result.mayPickup(getPlayer())) return;
        var output = result.getItem().copy();
        boolean toPlayer = action == InventoryAction.CRAFT_SHIFT || action == InventoryAction.CRAFT_ALL;
        int crafts = switch (action) {
            case CRAFT_ITEM -> 1;
            case CRAFT_STACK, CRAFT_SHIFT -> output.getMaxStackSize() / output.getCount();
            case CRAFT_ALL -> output.getMaxStackSize() * Inventory.INVENTORY_SIZE / output.getCount();
            default -> 0;
        };
        InternalInventory target = toPlayer ? new PlayerInternalInventory(getPlayerInventory())
                : new CarriedItemInventory(this);
        var page = workPage;
        int inputCount = workInputCount();
        for (int i = 0; i < crafts; i++) {
            // Components, batch size, XP and destination capacity are rechecked before every native callback.
            if (workPage != page || !isMainWorkPage() || !allowsSlot(result.index)
                    || !ItemStack.matches(output, result.getItem()) || !result.mayPickup(getPlayer())) break;
            boolean oversizedSingle = action == InventoryAction.CRAFT_ITEM && getCarried().isEmpty();
            if (!oversizedSingle && !target.simulateAdd(output).isEmpty()) break;
            var before = new ArrayList<ItemStack>(inputCount);
            for (int j = 0; j < inputCount; j++) before.add(engine.getSlot(j).getItem().copy());
            ResourceLocation stoneId = engine == stonecutter && stonecutter.getSelectedRecipeIndex() >= 0
                    ? stonecutter.getRecipes().get(stonecutter.getSelectedRecipeIndex()).id() : null;

            var crafted = result.remove(output.getCount());
            if (crafted.isEmpty()) break;
            result.onTake(getPlayer(), crafted.copy());
            // Replenish only emptied inputs, from ME, using the complete original item key.
            // Variable-count anvil recipes restore the amount the native menu actually consumed.
            var filter = ViewCellItem.createItemFilter(getViewCells());
            if (canInteractWithGrid()) for (int j = 0; j < inputCount; j++) {
                var input = engine.getSlot(j);
                var key = AEItemKey.of(before.get(j));
                if (input.hasItem() || key == null || !isKeyVisible(key) || filter != null && !filter.isListed(key)) continue;
                long extracted = StorageHelper.poweredExtraction(getEnergySource(), storage, key,
                        before.get(j).getCount(), getActionSource());
                if (extracted > 0) input.set(key.toStack((int) extracted));
            }
            // Native stonecutting resets selection when its last input is consumed.
            if (stoneId != null) selectStoneRecipe(stoneId);

            if (oversizedSingle) setCarried(crafted);
            else {
                var remaining = target.addItems(crafted);
                if (!remaining.isEmpty()) { getPlayer().drop(remaining, false); break; }
            }
        }
        broadcastChanges();
    }

    private void selectStoneRecipe(ResourceLocation id) {
        for (int i = 0; i < stonecutter.getNumRecipes(); i++) {
            if (stonecutter.getRecipes().get(i).id().equals(id)) {
                stonecutter.clickMenuButton(getPlayer(), i);
                return;
            }
        }
    }

    public void clearWorkInputs(boolean toPlayer) {
        if (!isMainWorkPage()) return;
        if (workPage == TianshuWorkPage.CRAFTING) {
            if (toPlayer) super.clearToPlayerInventory(); else super.clearCraftingGrid();
        } else if (isClientSide()) sendClientAction(toPlayer ? "clearWorkToPlayer" : "clearWorkToNetwork", workPage);
        else clearWorkInputs(workPage, toPlayer);
    }

    @Override public void clearToPlayerInventory() {
        if (isMainWorkPage() && workPage == TianshuWorkPage.CRAFTING) super.clearToPlayerInventory();
    }

    private void clearWorkInputs(TianshuWorkPage page, boolean toPlayer) {
        if (!isMainWorkPage() || workPage != page || workEngine() == null) return;
        var engine = workEngine();
        var playerInv = new PlayerInternalInventory(getPlayerInventory());
        for (int i = 0; i < workInputCount(); i++) {
            var input = engine.getSlot(i);
            var remaining = input.getItem().copy();
            if (toPlayer) {
                // Native clear-to-player order: filled slots first, hotbar right-to-left, then main inventory.
                for (boolean empty : new boolean[]{false, true}) {
                    for (int j = 8; j >= 0; j--) if (playerInv.getStackInSlot(j).isEmpty() == empty)
                        remaining = playerInv.getSlotInv(j).addItems(remaining);
                    for (int j = 9; j < Inventory.INVENTORY_SIZE; j++) if (playerInv.getStackInSlot(j).isEmpty() == empty)
                        remaining = playerInv.getSlotInv(j).addItems(remaining);
                }
            } else remaining.shrink(transferStackToMenu(remaining.copy()));
            input.set(remaining);
        }
        broadcastChanges();
    }

    private List<Predicate<ItemStack>> workIngredients(Recipe<?> recipe) {
        if (recipe instanceof SmithingRecipe smith) return List.of(smith::isTemplateIngredient, smith::isBaseIngredient, smith::isAdditionIngredient);
        if (recipe instanceof StonecutterRecipe stone) return List.of(stack -> stone.matches(new SingleRecipeInput(stack), getPlayer().level()));
        return List.of();
    }

    /** Client hint only; extraction and recipe identity are always checked again by the server. */
    public boolean canFillWorkRecipe(Recipe<?> recipe) {
        if (!isMainWorkPage()) return false;
        var ingredients = workIngredients(recipe);
        if (ingredients.isEmpty()) return false;
        var engine = recipe instanceof SmithingRecipe ? smithing : stonecutter;
        var filter = ViewCellItem.createItemFilter(getViewCells());
        for (int i = 0; i < ingredients.size(); i++) {
            var predicate = ingredients.get(i);
            if (!engine.getSlot(i).getItem().isEmpty() && predicate.test(engine.getSlot(i).getItem())) return true;
            for (int j = 0; j < getPlayerInventory().items.size(); j++) {
                var stack = getPlayerInventory().getItem(j);
                if (!isPlayerInventorySlotLocked(j) && !stack.isEmpty() && predicate.test(stack)) return true;
            }
            var repo = getClientRepo();
            if (repo != null && getLinkStatus().connected()) for (var entry : repo.getAllEntries()) {
                if (entry.getStoredAmount() > 0 && entry.getWhat() instanceof AEItemKey key
                        && (filter == null || filter.isListed(key))
                        && predicate.test(key.getReadOnlyStack())) return true;
            }
        }
        return false;
    }

    public void fillWorkRecipe(String recipeName) {
        if (recipeName.length() > 256) return;
        if (isClientSide()) { sendClientAction("workRecipe", recipeName); return; }
        if (!isMainWorkPage() || !isValidMenu() || !stillValid(getPlayer())) return;
        var id = ResourceLocation.tryParse(recipeName);
        if (id == null) return;
        var holder = getPlayer().level().getRecipeManager().byKey(id).orElse(null);
        if (holder == null) return;
        var ingredients = workIngredients(holder.value());
        if (ingredients.isEmpty()) return;
        AbstractContainerMenu engine;
        if (holder.value() instanceof SmithingRecipe) { workPage = TianshuWorkPage.SMITHING; engine = smithing; }
        else { workPage = TianshuWorkPage.STONECUTTING; engine = stonecutter; }
        updateSlotAccess();
        var filter = ViewCellItem.createItemFilter(getViewCells());
        for (int i = 0; i < ingredients.size(); i++) {
            var slot = engine.getSlot(i);
            var predicate = ingredients.get(i);
            var current = slot.getItem();
            if (!current.isEmpty() && predicate.test(current)) continue;
            if (!current.isEmpty()) {
                var remaining = current.copy();
                var key = AEItemKey.of(remaining);
                if (canInteractWithGrid() && key != null) {
                    long inserted = StorageHelper.poweredInsert(getEnergySource(), storage, key, remaining.getCount(), getActionSource());
                    remaining.shrink((int) inserted);
                }
                getPlayerInventory().add(remaining);
                slot.set(remaining.isEmpty() ? ItemStack.EMPTY : remaining);
            }
            if (slot.hasItem() || predicate.test(ItemStack.EMPTY)) continue;
            ItemStack ingredient = ItemStack.EMPTY;
            // Match complete AEItemKeys, including equipment damage, enchantments and custom components.
            if (canInteractWithGrid() && getGridNode() != null) {
                var candidates = new ArrayList<AEItemKey>();
                var available = getGridNode().getGrid().getStorageService().getCachedInventory();
                for (var entry : available) {
                    if (entry.getLongValue() > 0 && entry.getKey() instanceof AEItemKey key
                            && isKeyVisible(key) && (filter == null || filter.isListed(key))
                            && predicate.test(key.getReadOnlyStack())) candidates.add(key);
                }
                candidates.sort((a, b) -> Long.compare(available.get(b), available.get(a)));
                for (var key : candidates) {
                    if (StorageHelper.poweredExtraction(getEnergySource(), storage, key, 1, getActionSource()) > 0) {
                        ingredient = key.toStack(); break;
                    }
                }
            }
            if (ingredient.isEmpty()) for (int j = 0; j < getPlayerInventory().items.size(); j++) {
                var stack = getPlayerInventory().getItem(j);
                if (!isPlayerInventorySlotLocked(j) && !stack.isEmpty() && predicate.test(stack)) { ingredient = stack.split(1); break; }
            }
            slot.set(ingredient);
        }
        if (engine == stonecutter) selectStoneRecipe(id);
        broadcastChanges();
    }

    private void refreshCell() {
        var stack = getCell();
        cellCopyMode = cellWorkbench.getConfigManager().getSetting(Settings.COPY_MODE);
        if (configuredCell != stack) { cellConfigRow = cellUpgradeRow = 0; configuredCell = stack; }
        if (stack.getItem() instanceof ICellWorkbenchItem cell && cell.isEditable(stack)) {
            cellConfigSize = Math.min(CELL_CONFIG_SLOTS, cell.getConfigInventory(stack).size());
            cellUpgradeSize = Math.min(8, cell.getUpgrades(stack).size());
            cellFuzzyMode = cell.getFuzzyMode(stack);
        } else {
            cellConfigSize = cellUpgradeSize = 0;
            cellFuzzyMode = FuzzyMode.IGNORE_ALL;
        }
        if (cellCopyMode == CopyMode.KEEP_ON_REMOVE) cellConfigSize = CELL_CONFIG_SLOTS;
        cellConfigRow = Math.clamp(cellConfigRow, 0, getMaxCellConfigRow());
        cellUpgradeRow = Math.clamp(cellUpgradeRow, 0, getMaxCellUpgradeRow());
    }

    public void updateSlotAccess() {
        for (var semantic : List.of(SlotSemantics.CRAFTING_GRID, SlotSemantics.CRAFTING_RESULT)) {
            for (var slot : getSlots(semantic)) {
                if (slot instanceof AppEngSlot aeSlot) aeSlot.setActive(workPage == TianshuWorkPage.CRAFTING);
            }
        }
    }

    protected boolean allowsSlot(int index) {
        if (index < 0 || index >= slots.size()) return true;
        var slot = slots.get(index);
        if (slot instanceof AppEngSlot aeSlot && !aeSlot.isSlotEnabled()) return false;
        var semantic = getSlotSemantic(slot);
        if (semantic == Ae2ltSlotSemantics.TIANSHU_CELL && workPage != TianshuWorkPage.CELL) return false;
        return slot.isActive();
    }

    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (!allowsSlot(slot)) return;
        if (slot >= 0 && slot < slots.size() && isWorkResult(slots.get(slot))) {
            if (type == ClickType.PICKUP && (button == 0 || button == 1))
                craftWorkResult((TianshuWorkSlot) slots.get(slot), button == 1 ? InventoryAction.CRAFT_STACK : InventoryAction.CRAFT_ITEM);
            else if (type == ClickType.QUICK_MOVE) craftWorkResult((TianshuWorkSlot) slots.get(slot), InventoryAction.CRAFT_SHIFT);
            return;
        }
        super.clicked(slot, button, type, player);
    }
    @Override public void doAction(ServerPlayer player, InventoryAction action, int slot, long id) {
        if (!allowsSlot(slot)) return;
        if (slot >= 0 && slot < slots.size() && slots.get(slot) instanceof TianshuWorkSlot work) {
            if (work.result) craftWorkResult(work, action);
            else if (action == InventoryAction.MOVE_REGION) clearWorkInputs(false);
            return;
        }
        super.doAction(player, action, slot, id);
    }
    @Override public void setFilter(int slot, ItemStack stack) { if (allowsSlot(slot)) super.setFilter(slot, stack); }
    @Override public boolean canDragTo(Slot slot) { return allowsSlot(slot.index) && super.canDragTo(slot); }
    @Override public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return allowsSlot(slot.index) && !(slot instanceof TianshuWorkSlot work && work.result) && super.canTakeItemForPickAll(stack, slot);
    }

    @Override protected boolean isValidQuickMoveDestination(Slot slot, ItemStack stack, boolean fromPlayer) {
        // Like AE2's crafting matrix, work inputs are filled explicitly, not by a failed Shift-to-ME transfer.
        return !(slot instanceof TianshuWorkSlot) && allowsSlot(slot.index)
                && super.isValidQuickMoveDestination(slot, stack, fromPlayer);
    }

    private ItemStack quickMovePlayerStack(Player player, Slot source) {
        if (isClientSide() || !source.mayPickup(player) || !source.hasItem()) return ItemStack.EMPTY;
        int transferred = transferStackToMenu(source.getItem().copy());
        if (transferred > 0) source.remove(transferred);
        var remaining = source.getItem().copy();
        if (remaining.isEmpty()) return ItemStack.EMPTY;
        var destinations = getQuickMoveDestinationSlots(remaining, true);
        for (boolean occupied : new boolean[] {true, false}) {
            for (var destination : destinations) {
                if (destination.hasItem() == occupied) remaining = destination.safeInsert(remaining);
                if (remaining.isEmpty()) break;
            }
            if (remaining.isEmpty()) break;
        }
        if (!ItemStack.matches(source.getItem(), remaining)) source.setByPlayer(remaining);
        // AEBaseMenu's generic fallback writes directly to the first FakeSlot, including hidden pages.
        // Ghost configuration here uses explicit active-slot actions instead.
        return ItemStack.EMPTY;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size() || !allowsSlot(index)) return ItemStack.EMPTY;
        var slot = slots.get(index);
        if (slot instanceof TianshuWorkSlot work) {
            if (work.result) craftWorkResult(work, InventoryAction.CRAFT_SHIFT);
            else if (isServerSide() && slot.mayPickup(player)) {
                int transferred = transferStackToMenu(slot.getItem().copy());
                if (transferred > 0) slot.remove(transferred);
            }
            // Like AE2, handle the bounded action once instead of vanilla's repeated quick-move loop.
            return ItemStack.EMPTY;
        }
        if (isPlayerSideSlot(slot)) return quickMovePlayerStack(player, slot);
        return super.quickMoveStack(player, index);
    }

    @Override public void broadcastChanges() {
        if (isServerSide() && maintenanceSession != null) {
            refreshCell();
            if (boundTianshuTarget == null) {
                var target = tianshuHost.selectTianshuTarget();
                if (target != null) {
                    boundTianshuTarget = target;
                    tianshuSelectionRevision++;
                    maintenanceSession.invalidateTarget();
                }
            }
            var target = resolveBoundTianshu();
            maintenanceAvailable = target != null && target.getFunctionProfile().supportsInventoryMaintenance();
            anvilCost = anvil.getCost();
            stoneRecipe = stonecutter.getSelectedRecipeIndex();
        }
        super.broadcastChanges();
        if (isServerSide() && maintenanceSession != null) maintenanceSession.sendMaintenanceSummaryIfNeeded();
    }

    @Override public void onServerDataSync(it.unimi.dsi.fastutil.shorts.ShortSet fields) {
        super.onServerDataSync(fields);
        updateSlotAccess();
        anvil.setMaximumCost(anvilCost);
        if (stoneRecipe >= 0) stonecutter.clickMenuButton(getPlayer(), stoneRecipe);
    }

    @Override public void removed(Player player) {
        if (isServerSide() && !returnedInputs) {
            returnedInputs = true;
            // Only real inputs are returned. Card/config inventories remain components of the cell.
            for (var slot : extraInputs) {
                var stack = slot.remove(slot.getItem().getCount());
                if (!stack.isEmpty()) {
                    if (!player.isAlive() || player instanceof ServerPlayer serverPlayer && serverPlayer.hasDisconnected()) player.drop(stack, false);
                    else player.getInventory().placeItemBackInInventory(stack);
                }
            }
        }
        super.removed(player);
    }

    @Nullable private TianshuSupercomputerPortBlockEntity resolveBoundTianshu() { return tianshuHost.resolveTianshuTarget(boundTianshuTarget); }
    @Override public MEStorageMenu maintenanceMenu() { return this; }
    @Override public TianshuMaintenanceSession getMaintenanceSession() { return maintenanceSession; }
    @Override public int getTianshuSelectionRevision() { return tianshuSelectionRevision; }
    @Override public boolean isMaintenanceAvailable() { return maintenanceAvailable; }
    @Override public boolean isMaintainableView() { return maintainableView; }
    @Override public FakeSlot getGlobalReserveMarkSlot() { return globalReserveMarkSlot; }
    @Override public boolean showsCraftables() { return maintainableView || super.showsCraftables(); }
    @Override public void setMaintainableView(boolean enabled) {
        if (isClientSide()) sendClientAction("setMaintainableView", enabled);
        else maintainableView = enabled;
    }
}
