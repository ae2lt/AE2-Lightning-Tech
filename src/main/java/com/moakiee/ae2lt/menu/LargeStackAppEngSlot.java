package com.moakiee.ae2lt.menu;

import java.util.ArrayList;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import appeng.api.inventories.InternalInventory;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.AppEngSlot;

/**
 * Menu slot that honors the backing inventory's slot limit even when it
 * exceeds the item's vanilla max stack size.
 */
public class LargeStackAppEngSlot extends AppEngSlot {
    private final InternalInventory backingInventory;
    private final int backingSlot;
    private ItemStack synchronizedDisplayStack = ItemStack.EMPTY;
    private boolean hasSynchronizedDisplayStack;

    public LargeStackAppEngSlot(InternalInventory inventory, int slot) {
        super(inventory, slot);
        this.backingInventory = inventory;
        this.backingSlot = slot;
        setHideAmount(true);
        setNotDraggable();
    }

    @Override
    public ItemStack getItem() {
        if (!isSlotEnabled()) {
            return ItemStack.EMPTY;
        }

        // Client menu synchronization must never write AE2's wrapped display
        // stack into the block entity's real inventory. Keep that projection
        // local to the slot instead.
        if (isRemote() && hasSynchronizedDisplayStack) {
            return synchronizedDisplayStack;
        }

        return toPresentationStack(getBackingItem());
    }

    @Override
    public void initialize(ItemStack stack) {
        if (isRemote()) {
            setSynchronizedDisplayStack(stack);
        } else {
            super.initialize(stack);
        }
    }

    @Override
    public void set(ItemStack stack) {
        if (isRemote()) {
            setSynchronizedDisplayStack(stack);
        } else {
            super.set(stack);
        }
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return getMaxStackSize();
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return isSlotEnabled()
                && !stack.isEmpty()
                && !GenericStack.isWrapped(stack)
                && backingInventory.isItemValid(backingSlot, stack);
    }

    /**
     * Slot.safeInsert normally mutates the stack returned by getItem(). That is
     * only a presentation copy for an oversized slot, so insertion must go
     * through the real backing inventory instead.
     */
    @Override
    public ItemStack safeInsert(ItemStack stack, int increment) {
        if (stack.isEmpty() || increment <= 0 || !mayPlace(stack)) {
            return stack;
        }

        int offeredCount = Math.min(increment, stack.getCount());
        ItemStack offered = stack.copyWithCount(offeredCount);
        ItemStack rejected = backingInventory.insertItem(backingSlot, offered, false);
        int inserted = offeredCount - rejected.getCount();
        if (inserted > 0) {
            stack.shrink(inserted);
            setChanged();
        }
        return stack;
    }

    /**
     * Bound every generic extraction path to one legal carried stack. Repeated
     * Shift-clicks may drain more, but no individual ItemStack leaving this slot
     * can exceed its own native maximum.
     */
    @Override
    public ItemStack remove(int amount) {
        ItemStack actual = getBackingItem();
        if (actual.isEmpty() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        return backingInventory.extractItem(
                backingSlot,
                Math.min(amount, actual.getMaxStackSize()),
                false);
    }

    private ItemStack getBackingItem() {
        return backingInventory.getStackInSlot(backingSlot);
    }

    /** Returns the exact logical amount represented by this menu slot. */
    public long getDisplayedAmount() {
        ItemStack displayed = getItem();
        GenericStack wrapped = GenericStack.unwrapItemStack(displayed);
        return wrapped != null ? wrapped.amount() : displayed.getCount();
    }

    private void setSynchronizedDisplayStack(ItemStack stack) {
        synchronizedDisplayStack = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        hasSynchronizedDisplayStack = true;
    }

    private static ItemStack toPresentationStack(ItemStack actual) {
        if (actual.isEmpty() || GenericStack.isWrapped(actual)) {
            return actual;
        }

        AEItemKey key = AEItemKey.of(actual);
        if (key != null && actual.getCount() > key.getMaxStackSize()) {
            return GenericStack.wrapInItemStack(key, actual.getCount());
        }
        return actual;
    }

    private int insertIntoBacking(ItemStack stack, int amount) {
        if (amount <= 0) {
            return 0;
        }

        int offeredCount = Math.min(amount, stack.getCount());
        ItemStack offered = stack.copyWithCount(offeredCount);
        ItemStack rejected = backingInventory.insertItem(backingSlot, offered, false);
        int inserted = offeredCount - rejected.getCount();
        if (inserted > 0) {
            setChanged();
        }
        return inserted;
    }

    public static boolean mustRejectDirectExtraction(int button, ClickType clickType) {
        return clickType == ClickType.SWAP;
    }

    /**
     * Handles interactions that could otherwise materialize an oversized machine
     * stack in a player-owned location. This is called by the common menu on both
     * sides, so the server remains authoritative even for forged click packets.
     */
    public static boolean handleMenuInteraction(
            AEBaseMenu menu,
            int slotId,
            int button,
            ClickType clickType,
            Player player) {
        if (slotId < 0
                || slotId >= menu.slots.size()
                || !(menu.getSlot(slotId) instanceof LargeStackAppEngSlot slot)) {
            return false;
        }

        // Match the existing ME-terminal proxy pattern: the client consumes the
        // click locally without mutating its display projection; the server is
        // the only side allowed to touch the backing inventory.
        if (menu.isClientSide()) {
            return true;
        }

        if (mustRejectDirectExtraction(button, clickType)) {
            return true;
        }

        switch (clickType) {
            case PICKUP -> handlePickup(menu, slot, button, player);
            case QUICK_MOVE -> handleQuickMove(menu, slot, player);
            case THROW -> handleThrow(slot, button, player);
            case CLONE -> handleClone(menu, slot, player);
            case PICKUP_ALL -> handlePickupAll(menu, slot, player);
            case QUICK_CRAFT, SWAP -> {
                // Dragging and direct hotbar/offhand swaps are deliberately not
                // delegated to vanilla for a managed large-stack slot.
            }
        }
        return true;
    }

    private static void handlePickup(
            AEBaseMenu menu,
            LargeStackAppEngSlot slot,
            int button,
            Player player) {
        if (button != 0 && button != 1) {
            return;
        }

        var carried = menu.getCarried();
        var slotStack = slot.getBackingItem();
        boolean rightClick = button == 1;

        if (carried.isEmpty()) {
            if (slotStack.isEmpty() || !slot.canExtractBacking()) {
                return;
            }

            int nativeMax = slotStack.getMaxStackSize();
            int requested = rightClick
                    ? Math.min(nativeMax, Math.max(1, (int) Math.ceil(slotStack.getCount() / 2.0D)))
                    : nativeMax;
            var taken = slot.remove(requested);
            menu.setCarried(taken);
            slot.onTake(player, taken);
            slot.setChanged();
            return;
        }

        if (slotStack.isEmpty()) {
            if (!slot.mayPlace(carried)) {
                return;
            }

            int toMove = Math.min(rightClick ? 1 : carried.getCount(), slot.getMaxStackSize(carried));
            if (toMove <= 0) {
                return;
            }

            int inserted = slot.insertIntoBacking(carried, toMove);
            carried.shrink(inserted);
            menu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
            return;
        }

        if (ItemStack.isSameItemSameTags(slotStack, carried)) {
            if (!slot.mayPlace(carried)) {
                if (!slot.canExtractBacking()) {
                    return;
                }

                int cursorRoom = carried.getMaxStackSize() - carried.getCount();
                ItemStack taken = slot.remove(cursorRoom);
                if (!taken.isEmpty()) {
                    carried.grow(taken.getCount());
                    menu.setCarried(carried);
                    slot.onTake(player, taken);
                    slot.setChanged();
                }
                return;
            }

            int room = slot.getMaxStackSize(carried) - slotStack.getCount();
            int requested = Math.min(rightClick ? 1 : carried.getCount(), room);
            int inserted = slot.insertIntoBacking(carried, requested);
            carried.shrink(inserted);
            menu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
            return;
        }

        if (!slot.mayPlace(carried)
                || !slot.canExtractBacking()
                || carried.getCount() > slot.getMaxStackSize(carried)
                || slotStack.getCount() > slotStack.getMaxStackSize()) {
            return;
        }

        slot.set(carried);
        menu.setCarried(slotStack.copy());
    }

    private static void handleQuickMove(
            AEBaseMenu menu,
            LargeStackAppEngSlot slot,
            Player player) {
        ItemStack actual = slot.getBackingItem();
        if (actual.isEmpty() || !slot.canExtractBacking()) {
            return;
        }

        ItemStack taken = slot.remove(actual.getMaxStackSize());
        if (taken.isEmpty()) {
            return;
        }

        ItemStack original = taken.copy();
        ItemStack remainder = moveIntoPlayerInventory(menu, taken);
        int moved = original.getCount() - remainder.getCount();

        if (!remainder.isEmpty()) {
            int restored = slot.insertIntoBacking(remainder, remainder.getCount());
            remainder.shrink(restored);
            if (!remainder.isEmpty()) {
                player.drop(remainder, false);
            }
        }

        if (moved > 0) {
            ItemStack movedStack = original.copyWithCount(moved);
            slot.onTake(player, movedStack);
            slot.setChanged();
        }
    }

    private static ItemStack moveIntoPlayerInventory(AEBaseMenu menu, ItemStack stack) {
        var destinations = new ArrayList<Slot>(menu.getSlots(SlotSemantics.PLAYER_INVENTORY));
        destinations.addAll(menu.getSlots(SlotSemantics.PLAYER_HOTBAR));

        ItemStack remainder = stack;
        for (Slot destination : destinations) {
            if (!destination.hasItem()) {
                continue;
            }
            remainder = destination.safeInsert(remainder);
            if (remainder.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }
        for (Slot destination : destinations) {
            if (destination.hasItem()) {
                continue;
            }
            remainder = destination.safeInsert(remainder);
            if (remainder.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }
        return remainder;
    }

    private static void handleThrow(LargeStackAppEngSlot slot, int button, Player player) {
        ItemStack actual = slot.getBackingItem();
        if (actual.isEmpty() || !slot.canExtractBacking()) {
            return;
        }

        int amount = button == 1 ? actual.getMaxStackSize() : 1;
        ItemStack taken = slot.remove(amount);
        if (!taken.isEmpty()) {
            player.drop(taken, true);
            slot.onTake(player, taken);
            slot.setChanged();
        }
    }

    private static void handleClone(AEBaseMenu menu, LargeStackAppEngSlot slot, Player player) {
        if (!player.getAbilities().instabuild) {
            return;
        }

        ItemStack actual = slot.getBackingItem();
        if (!actual.isEmpty()) {
            menu.setCarried(actual.copyWithCount(actual.getMaxStackSize()));
        }
    }

    private static void handlePickupAll(AEBaseMenu menu, LargeStackAppEngSlot slot, Player player) {
        ItemStack carried = menu.getCarried();
        ItemStack actual = slot.getBackingItem();
        if (carried.isEmpty()
                || actual.isEmpty()
                || !ItemStack.isSameItemSameTags(actual, carried)
                || !slot.canExtractBacking()) {
            return;
        }

        int room = carried.getMaxStackSize() - carried.getCount();
        ItemStack taken = slot.remove(room);
        if (!taken.isEmpty()) {
            carried.grow(taken.getCount());
            menu.setCarried(carried);
            slot.onTake(player, taken);
            slot.setChanged();
        }
    }

    private boolean canExtractBacking() {
        return isSlotEnabled()
                && !backingInventory.extractItem(backingSlot, 1, true).isEmpty();
    }
}
