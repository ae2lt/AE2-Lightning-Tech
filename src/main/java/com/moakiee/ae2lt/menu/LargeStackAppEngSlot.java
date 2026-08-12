package com.moakiee.ae2lt.menu;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

import appeng.api.inventories.InternalInventory;
import appeng.menu.AEBaseMenu;
import appeng.menu.slot.AppEngSlot;

/**
 * Menu slot that honors the backing inventory's slot limit even when it
 * exceeds the item's vanilla max stack size.
 */
public class LargeStackAppEngSlot extends AppEngSlot {
    private final InternalInventory backingInventory;
    private final int backingSlot;

    public LargeStackAppEngSlot(InternalInventory inventory, int slot) {
        super(inventory, slot);
        this.backingInventory = inventory;
        this.backingSlot = slot;
        setHideAmount(true);
        setNotDraggable();
    }

    /**
     * Never expose an oversized real item stack through the menu slot. The
     * backing inventory may legally contain more than the item's native stack
     * size, but vanilla menu code and third-party item hooks must only ever see
     * a normal-sized presentation copy.
     */
    @Override
    public ItemStack getItem() {
        if (!isSlotEnabled()) {
            return ItemStack.EMPTY;
        }
        ItemStack actual = getBackingItem();
        if (actual.isEmpty() || actual.getCount() <= actual.getMaxStackSize()) {
            return actual;
        }
        return actual.copyWithCount(actual.getMaxStackSize());
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return getMaxStackSize();
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

        if (mustRejectDirectExtraction(button, clickType)) {
            return true;
        }

        return clickType == ClickType.PICKUP && handlePickup(menu, slot, button, player);
    }

    private static boolean handlePickup(
            AEBaseMenu menu,
            LargeStackAppEngSlot slot,
            int button,
            Player player) {
        if (button != 0 && button != 1) {
            return false;
        }

        var carried = menu.getCarried();
        var slotStack = slot.getBackingItem();
        boolean rightClick = button == 1;

        if (carried.isEmpty()) {
            if (slotStack.isEmpty() || !slot.mayPickup(player)) {
                return true;
            }

            int nativeMax = slotStack.getMaxStackSize();
            int requested = rightClick
                    ? Math.min(nativeMax, Math.max(1, (int) Math.ceil(slotStack.getCount() / 2.0D)))
                    : nativeMax;
            var taken = slot.remove(requested);
            menu.setCarried(taken);
            slot.onTake(player, taken);
            slot.setChanged();
            return true;
        }

        if (slotStack.isEmpty()) {
            if (!slot.mayPlace(carried)) {
                return true;
            }

            int toMove = Math.min(rightClick ? 1 : carried.getCount(), slot.getMaxStackSize(carried));
            if (toMove <= 0) {
                return true;
            }

            int inserted = slot.insertIntoBacking(carried, toMove);
            carried.shrink(inserted);
            menu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
            return true;
        }

        if (ItemStack.isSameItemSameComponents(slotStack, carried)) {
            if (!slot.mayPlace(carried)) {
                if (!slot.mayPickup(player)) {
                    return true;
                }

                int cursorRoom = carried.getMaxStackSize() - carried.getCount();
                ItemStack taken = slot.remove(cursorRoom);
                if (!taken.isEmpty()) {
                    carried.grow(taken.getCount());
                    menu.setCarried(carried);
                    slot.onTake(player, taken);
                    slot.setChanged();
                }
                return true;
            }

            int room = slot.getMaxStackSize(carried) - slotStack.getCount();
            int requested = Math.min(rightClick ? 1 : carried.getCount(), room);
            int inserted = slot.insertIntoBacking(carried, requested);
            carried.shrink(inserted);
            menu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
            return true;
        }

        if (!slot.mayPlace(carried)
                || !slot.mayPickup(player)
                || carried.getCount() > slot.getMaxStackSize(carried)
                || slotStack.getCount() > slotStack.getMaxStackSize()) {
            return true;
        }

        slot.set(carried);
        menu.setCarried(slotStack.copy());
        return true;
    }
}
