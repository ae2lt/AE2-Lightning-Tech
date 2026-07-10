package com.moakiee.ae2lt.menu;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ArrayListMultimap;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity;
import com.moakiee.ae2lt.item.OverloadedFilterComponentItem;
import com.moakiee.ae2lt.logic.OverloadedInterfaceLogic;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.upgrades.Upgrades;
import appeng.api.stacks.GenericStack;
import appeng.helpers.InterfaceLogicHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.InterfaceMenu;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.implementations.SetStockAmountMenu;
import appeng.api.inventories.InternalInventory;
import appeng.menu.slot.AppEngSlot;

public class OverloadedInterfaceMenu extends InterfaceMenu implements FrequencyBindingMenu {

    private static final MenuTypeBuilder.MenuFactory<OverloadedInterfaceMenu, InterfaceLogicHost> FACTORY =
            OverloadedInterfaceMenu::new;

    public static final MenuType<OverloadedInterfaceMenu> TYPE = MenuTypeBuilder
            .create(FACTORY, InterfaceLogicHost.class)
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(
                    AE2LightningTech.MODID, "overloaded_interface"));

    private static final int SLOTS_PER_PAGE = 18;

    private static final Field F_SEMANTIC_BY_SLOT;
    private static final Field F_SLOTS_BY_SEMANTIC;

    static {
        OverloadedSlotSemantics.init();
        try {
            F_SEMANTIC_BY_SLOT = AEBaseMenu.class.getDeclaredField("semanticBySlot");
            F_SEMANTIC_BY_SLOT.setAccessible(true);
            F_SLOTS_BY_SEMANTIC = AEBaseMenu.class.getDeclaredField("slotsBySemantic");
            F_SLOTS_BY_SEMANTIC.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to reflect AEBaseMenu slot maps", e);
        }
    }

    @GuiSync(20)
    public int currentPage;

    @GuiSync(21)
    public int totalPages;

    @GuiSync(22)
    public int interfaceMode;

    @GuiSync(23)
    public int exportMode;

    @GuiSync(24)
    public int importMode;

    @GuiSync(25)
    public int energyDirOrdinal;

    @GuiSync(26)
    public int ioSpeedMode;

    @GuiSync(27)
    public long unlimitedBits;

    private final InterfaceLogicHost host;
    private final Set<Slot> storageSlotSet;
    private final Set<Slot> containerSlotSet;
    private final List<Slot> allConfigSlots;
    private Slot filterSlot;
    private int lastShownPage = -1;

    public OverloadedInterfaceMenu(int id, Inventory playerInventory, InterfaceLogicHost host) {
        super(TYPE, id, playerInventory, host);
        this.host = host;

        remapSlotSemantics();

        if (host instanceof OverloadedInterfaceBlockEntity be) {
            var filterSlot = new OverloadedFilterSlot(be.getFilterInv(), 0);
            filterSlot.setNotDraggable();
            this.filterSlot = this.addSlot(filterSlot, SlotSemantics.UPGRADE);
            Ae2ltSlotBackgrounds.withBackground(this.filterSlot, Ae2ltSlotBackgrounds.FILTER_COMPONENT);
        }

        var logic = host.getInterfaceLogic();
        int configSize = logic.getConfig().size();
        this.totalPages = Math.max(1, (configSize + SLOTS_PER_PAGE - 1) / SLOTS_PER_PAGE);

        this.allConfigSlots = new ArrayList<>();
        for (var sem : OverloadedSlotSemantics.CONFIG_PATTERN) {
            allConfigSlots.addAll(getSlots(sem));
        }

        this.storageSlotSet = new HashSet<>();
        for (var sem : OverloadedSlotSemantics.STORAGE_PATTERN) {
            storageSlotSet.addAll(getSlots(sem));
        }
        var cSlots = new HashSet<Slot>(storageSlotSet);
        cSlots.addAll(allConfigSlots);
        cSlots.addAll(getSlots(SlotSemantics.UPGRADE));
        this.containerSlotSet = cSlots;

        registerClientAction("nextPage", this::nextPage);
        registerClientAction("prevPage", this::prevPage);
        registerClientAction("cycleInterfaceMode", this::cycleInterfaceMode);
        registerClientAction("cycleExportMode", this::cycleExportMode);
        registerClientAction("cycleImportMode", this::cycleImportMode);
        registerClientAction("cycleEnergyDir", this::cycleEnergyDir);
        registerClientAction("cycleIOSpeed", this::cycleIOSpeed);
        registerClientAction("toggleUnlimited", Integer.class, this::toggleUnlimited);

        syncFromBE();
        int startPage = (host instanceof OverloadedInterfaceBlockEntity be)
                ? Math.min(be.getLastViewedPage(), totalPages - 1) : 0;
        showPage(Math.max(0, startPage));
    }

    @SuppressWarnings("unchecked")
    private void remapSlotSemantics() {
        try {
            var semanticBySlot = (Map<Slot, SlotSemantic>) F_SEMANTIC_BY_SLOT.get(this);
            var slotsBySemantic = (ArrayListMultimap<SlotSemantic, Slot>) F_SLOTS_BY_SEMANTIC.get(this);

            var configSlots = new ArrayList<>(slotsBySemantic.get(SlotSemantics.CONFIG));
            var storageSlots = new ArrayList<>(slotsBySemantic.get(SlotSemantics.STORAGE));

            slotsBySemantic.removeAll(SlotSemantics.CONFIG);
            slotsBySemantic.removeAll(SlotSemantics.STORAGE);

            for (int i = 0; i < configSlots.size(); i++) {
                int page = i / 18;
                int row = (i % 18) / 9;
                var semantic = OverloadedSlotSemantics.CONFIG_PATTERN[2 * page + row];
                var slot = configSlots.get(i);
                semanticBySlot.put(slot, semantic);
                slotsBySemantic.put(semantic, slot);
            }

            for (int i = 0; i < storageSlots.size(); i++) {
                int page = i / 18;
                int row = (i % 18) / 9;
                var semantic = OverloadedSlotSemantics.STORAGE_PATTERN[2 * page + row];
                var slot = storageSlots.get(i);
                semanticBySlot.put(slot, semantic);
                slotsBySemantic.put(semantic, slot);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to remap slot semantics", e);
        }
    }

    public List<Slot> getAllConfigSlots() {
        return allConfigSlots;
    }

    public Slot getFilterSlot() {
        return filterSlot;
    }

    private void syncFromBE() {
        if (host instanceof OverloadedInterfaceBlockEntity be) {
            interfaceMode = be.getInterfaceMode().ordinal();
            ioSpeedMode   = be.getIOSpeedMode().ordinal();
            exportMode    = be.getExportMode().ordinal();
            importMode    = be.getImportMode().ordinal();
            var dir = be.getEnergyOutputDir();
            energyDirOrdinal = dir != null ? dir.get3DDataValue() : -1;
            long bits = 0;
            for (int i = 0; i < OverloadedInterfaceBlockEntity.SLOT_COUNT; i++)
                if (be.isSlotUnlimited(i)) bits |= (1L << i);
            unlimitedBits = bits;
        }
    }

    // ── Pagination ───────────────────────────────────────────────────────

    public void showPage(int page) {
        if (page < 0 || page >= totalPages) return;
        lastShownPage = page;
        currentPage = page;
        if (host instanceof OverloadedInterfaceBlockEntity be) {
            be.setLastViewedPage(page);
        }

        for (int group = 0; group < 4; group++) {
            boolean active = (page == group / 2);
            var cfgSlots = getSlots(OverloadedSlotSemantics.CONFIG_PATTERN[group]);
            var stoSlots = getSlots(OverloadedSlotSemantics.STORAGE_PATTERN[group]);
            for (var s : cfgSlots) {
                if (s instanceof AppEngSlot as) as.setActive(active);
            }
            for (var s : stoSlots) {
                if (s instanceof AppEngSlot as) as.setActive(active);
            }
        }
    }

    public void nextPage() {
        if (isClientSide()) {
            sendClientAction("nextPage");
        }
        showPage(currentPage + 1);
    }

    public void prevPage() {
        if (isClientSide()) {
            sendClientAction("prevPage");
        }
        showPage(currentPage - 1);
    }

    // ── Mode cycles ──────────────────────────────────────────────────────

    public void cycleInterfaceMode() {
        if (isClientSide()) { sendClientAction("cycleInterfaceMode"); return; }
        if (host instanceof OverloadedInterfaceBlockEntity be) {
            var modes = OverloadedInterfaceBlockEntity.InterfaceMode.values();
            be.setInterfaceMode(modes[(interfaceMode + 1) % modes.length]);
            interfaceMode = be.getInterfaceMode().ordinal();
        }
    }

    public void cycleExportMode() {
        if (isClientSide()) { sendClientAction("cycleExportMode"); return; }
        if (host instanceof OverloadedInterfaceBlockEntity be) {
            var modes = OverloadedInterfaceBlockEntity.ExportMode.values();
            be.setExportMode(modes[(exportMode + 1) % modes.length]);
            exportMode = be.getExportMode().ordinal();
        }
    }

    public void cycleImportMode() {
        if (isClientSide()) { sendClientAction("cycleImportMode"); return; }
        if (host instanceof OverloadedInterfaceBlockEntity be) {
            var modes = OverloadedInterfaceBlockEntity.ImportMode.values();
            be.setImportMode(modes[(importMode + 1) % modes.length]);
            importMode = be.getImportMode().ordinal();
        }
    }

    public void cycleEnergyDir() {
        if (isClientSide()) { sendClientAction("cycleEnergyDir"); return; }
        if (host instanceof OverloadedInterfaceBlockEntity be) {
            int next = energyDirOrdinal + 1;
            if (next >= 6) next = -1;
            be.setEnergyOutputDir(next >= 0 ? Direction.from3DDataValue(next) : null);
            var dir = be.getEnergyOutputDir();
            energyDirOrdinal = dir != null ? dir.get3DDataValue() : -1;
        }
    }

    public void cycleIOSpeed() {
        if (isClientSide()) { sendClientAction("cycleIOSpeed"); return; }
        if (host instanceof OverloadedInterfaceBlockEntity be) {
            var modes = OverloadedInterfaceBlockEntity.IOSpeedMode.values();
            be.setIOSpeedMode(modes[(ioSpeedMode + 1) % modes.length]);
            ioSpeedMode = be.getIOSpeedMode().ordinal();
        }
    }

    public void toggleUnlimited(int slot) {
        if (isClientSide()) { sendClientAction("toggleUnlimited", slot); return; }
        if (host instanceof OverloadedInterfaceBlockEntity be) {
            boolean nowUnlimited = !be.isSlotUnlimited(slot);
            be.setSlotUnlimited(slot, nowUnlimited);
            if (nowUnlimited) {
                var logic = (OverloadedInterfaceLogic) host.getInterfaceLogic();
                var key = logic.getConfig().getKey(slot);
                if (key != null) {
                    logic.setConfigStackSuppressed(slot, new GenericStack(key, 1));
                }
            }
            long bits = 0;
            for (int i = 0; i < OverloadedInterfaceBlockEntity.SLOT_COUNT; i++)
                if (be.isSlotUnlimited(i)) bits |= (1L << i);
            unlimitedBits = bits;
        }
    }

    public boolean isSlotUnlimited(int slot) {
        return (unlimitedBits & (1L << slot)) != 0;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ME Terminal-style proxy slot interaction
    //  Storage slots are display-only; all click actions handled here.
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < slots.size() && storageSlotSet.contains(slots.get(slotId))) {
            if (!isClientSide()) {
                handleStorageInteraction(slots.get(slotId), button, clickType, player);
            }
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    private OverloadedInterfaceLogic.ProxiedStorageInv getProxy() {
        var logic = host.getInterfaceLogic();
        if (logic instanceof OverloadedInterfaceLogic ol) {
            return ol.getProxiedStorage();
        }
        return null;
    }

    private void handleStorageInteraction(Slot slot, int button, ClickType clickType, Player player) {
        var proxy = getProxy();
        if (proxy == null) return;
        int idx = slot.getContainerSlot();

        switch (clickType) {
            case PICKUP -> handlePickup(proxy, idx, button, player);
            case QUICK_MOVE -> handleQuickMove(proxy, idx, player);
            case THROW -> handleThrow(proxy, idx, button, player);
            case SWAP -> handleSwap(proxy, idx, button, player);
            case CLONE -> handleClone(proxy, idx, player);
            default -> {}
        }
    }

    private void handlePickup(OverloadedInterfaceLogic.ProxiedStorageInv proxy,
                              int idx, int button, Player player) {
        var carried = getCarried();
        if (carried.isEmpty()) {
            var key = proxy.cfg().getKey(idx);
            if (!(key instanceof AEItemKey itemKey)) return;
            long cap = proxy.capForSlot(idx);
            int maxStack = itemKey.getMaxStackSize();
            long maxExtract = Math.min(cap, maxStack);
            if (button == 1) maxExtract = Math.max(1, (maxExtract + 1) / 2);
            long extracted = proxy.proxyExtract(key, maxExtract, Actionable.MODULATE);
            if (extracted > 0) {
                setCarried(itemKey.toStack((int) Math.min(extracted, Integer.MAX_VALUE)));
            }
        } else {
            var itemKey = AEItemKey.of(carried);
            if (itemKey == null) return;
            int toInsert = (button == 1) ? 1 : carried.getCount();
            long inserted = proxy.proxyInsert(itemKey, toInsert, Actionable.MODULATE);
            if (inserted > 0) {
                carried.shrink((int) inserted);
                if (carried.isEmpty()) setCarried(ItemStack.EMPTY);
            }
        }
    }

    private void handleQuickMove(OverloadedInterfaceLogic.ProxiedStorageInv proxy,
                                 int idx, Player player) {
        var key = proxy.cfg().getKey(idx);
        if (!(key instanceof AEItemKey itemKey)) return;
        long cap = proxy.capForSlot(idx);
        int maxStack = itemKey.getMaxStackSize();
        long maxExtract = Math.min(cap, maxStack);
        long extracted = proxy.proxyExtract(key, maxExtract, Actionable.MODULATE);
        if (extracted > 0) {
            var stack = itemKey.toStack((int) Math.min(extracted, Integer.MAX_VALUE));
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
    }

    private void handleThrow(OverloadedInterfaceLogic.ProxiedStorageInv proxy,
                             int idx, int button, Player player) {
        var key = proxy.cfg().getKey(idx);
        if (!(key instanceof AEItemKey itemKey)) return;
        long maxExtract = (button == 1)
                ? Math.min(proxy.capForSlot(idx), itemKey.getMaxStackSize())
                : 1;
        long extracted = proxy.proxyExtract(key, maxExtract, Actionable.MODULATE);
        if (extracted > 0) {
            player.drop(itemKey.toStack((int) Math.min(extracted, Integer.MAX_VALUE)), true);
        }
    }

    private void handleSwap(OverloadedInterfaceLogic.ProxiedStorageInv proxy,
                            int idx, int button, Player player) {
        if (button < 0 || button > 8) return;
        var hotbarStack = player.getInventory().getItem(button);
        var key = proxy.cfg().getKey(idx);

        boolean wantInsert  = !hotbarStack.isEmpty();
        boolean wantExtract = key instanceof AEItemKey;

        // Simulate both sides before committing
        long simInserted  = 0;
        AEItemKey hbKey   = null;
        long simExtracted = 0;
        AEItemKey itemKey = null;

        if (wantInsert) {
            hbKey = AEItemKey.of(hotbarStack);
            if (hbKey != null) {
                simInserted = proxy.proxyInsert(hbKey, hotbarStack.getCount(), Actionable.SIMULATE);
            }
        }
        if (wantExtract) {
            itemKey = (AEItemKey) key;
            long cap = proxy.capForSlot(idx);
            long maxExtract = Math.min(cap, itemKey.getMaxStackSize());
            simExtracted = proxy.proxyExtract(key, maxExtract, Actionable.SIMULATE);
        }

        if (simInserted <= 0 && simExtracted <= 0) return;

        // Modulate: extract first, then insert
        ItemStack extractedStack = ItemStack.EMPTY;
        if (simExtracted > 0) {
            long extracted = proxy.proxyExtract(key, simExtracted, Actionable.MODULATE);
            if (extracted > 0) {
                extractedStack = itemKey.toStack((int) Math.min(extracted, Integer.MAX_VALUE));
            }
        }

        if (simInserted > 0 && hbKey != null) {
            long inserted = proxy.proxyInsert(hbKey, hotbarStack.getCount(), Actionable.MODULATE);
            if (inserted > 0) hotbarStack.shrink((int) inserted);
        }

        if (!extractedStack.isEmpty()) {
            if (hotbarStack.isEmpty()) {
                player.getInventory().setItem(button, extractedStack);
            } else if (!player.getInventory().add(extractedStack)) {
                player.drop(extractedStack, false);
            }
        }
    }

    private void handleClone(OverloadedInterfaceLogic.ProxiedStorageInv proxy,
                             int idx, Player player) {
        if (!player.isCreative()) return;
        var key = proxy.cfg().getKey(idx);
        if (key instanceof AEItemKey itemKey) {
            setCarried(itemKey.toStack(itemKey.getMaxStackSize()));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int idx) {
        if (isClientSide()) return ItemStack.EMPTY;
        if (idx < 0 || idx >= slots.size()) return ItemStack.EMPTY;
        var slot = slots.get(idx);
        if (storageSlotSet.contains(slot)) return ItemStack.EMPTY;

        if (containerSlotSet.contains(slot)) {
            return super.quickMoveStack(player, idx);
        }

        if (slot.hasItem()) {
            var stack = slot.getItem();

            if (stack.getItem() instanceof OverloadedFilterComponentItem) {
                int beforeCount = stack.getCount();
                var superResult = super.quickMoveStack(player, idx);
                int afterCount = slot.hasItem() ? slot.getItem().getCount() : 0;
                if (afterCount < beforeCount) {
                    return superResult;
                }
            }

            if (Upgrades.isUpgradeCardItem(stack)) {
                int beforeCount = stack.getCount();
                var superResult = super.quickMoveStack(player, idx);
                int afterCount = slot.hasItem() ? slot.getItem().getCount() : 0;
                if (afterCount < beforeCount) {
                    return superResult;
                }
            }

            if (slot.hasItem()) {
                stack = slot.getItem();
                var proxy = getProxy();
                if (proxy != null) {
                    var key = AEItemKey.of(stack);
                    if (key != null) {
                        long inserted = proxy.proxyInsert(key, stack.getCount(), Actionable.MODULATE);
                        if (inserted > 0) {
                            slot.remove((int) inserted);
                            slot.setChanged();
                        }
                    }
                }
            }
        }
        return ItemStack.EMPTY;
    }

    // ══════════════════════════════════════════════════════════════════════

    @Override
    public void openSetAmountMenu(int configSlot) {
        if (isClientSide()) {
            sendClientAction(ACTION_OPEN_SET_AMOUNT, configSlot);
            return;
        }
        var stack = getHost().getConfig().getStack(configSlot);
        if (stack != null) {
            SetStockAmountMenu.open((ServerPlayer) getPlayer(), getLocator(), configSlot,
                    stack.what(), (int) stack.amount());
            if (getPlayer().containerMenu instanceof SetStockAmountMenu sam) {
                long cap = stack.what().getType().getAmountPerByte() * 1024L;
                try {
                    var f = SetStockAmountMenu.class.getDeclaredField("maxAmount");
                    f.setAccessible(true);
                    f.setInt(sam, (int) Math.min(cap, Integer.MAX_VALUE));
                    sam.broadcastChanges();
                } catch (ReflectiveOperationException e) {
                    org.slf4j.LoggerFactory.getLogger(OverloadedInterfaceMenu.class)
                            .warn("Failed to set maxAmount on SetStockAmountMenu", e);
                }
            }
        }
    }

    @Override
    public void broadcastChanges() {
        if (host instanceof OverloadedInterfaceBlockEntity be) {
            interfaceMode = be.getInterfaceMode().ordinal();
            ioSpeedMode   = be.getIOSpeedMode().ordinal();
            exportMode    = be.getExportMode().ordinal();
            importMode    = be.getImportMode().ordinal();
            var dir = be.getEnergyOutputDir();
            energyDirOrdinal = dir != null ? dir.get3DDataValue() : -1;
            long bits = 0;
            for (int i = 0; i < OverloadedInterfaceBlockEntity.SLOT_COUNT; i++)
                if (be.isSlotUnlimited(i)) bits |= (1L << i);
            unlimitedBits = bits;
        }
        super.broadcastChanges();
        if (lastShownPage != currentPage) {
            showPage(currentPage);
        }
    }

    private static final class OverloadedFilterSlot extends AppEngSlot {
        private OverloadedFilterSlot(InternalInventory inv, int invSlot) {
            super(inv, invSlot);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !stack.isEmpty() && stack.getItem() instanceof OverloadedFilterComponentItem;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
