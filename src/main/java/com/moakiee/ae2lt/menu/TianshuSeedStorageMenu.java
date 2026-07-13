package com.moakiee.ae2lt.menu;

import appeng.api.storage.StorageCells;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.slot.AppEngSlot;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.blockentity.TianshuSeedStorageBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class TianshuSeedStorageMenu extends AEBaseMenu {
    public static final MenuType<TianshuSeedStorageMenu> TYPE = MenuTypeBuilder
            .create(TianshuSeedStorageMenu::new, TianshuSeedStorageBlockEntity.class)
            .withMenuTitle(host -> Component.translatable("block.ae2lt.closed_loop_seed_storage"))
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(
                    AE2LightningTech.MODID, "closed_loop_seed_storage"));

    private final TianshuSeedStorageBlockEntity host;
    private final List<Slot> cellSlots = new ArrayList<>();

    public TianshuSeedStorageMenu(
            int id, Inventory playerInventory, TianshuSeedStorageBlockEntity host) {
        super(TYPE, id, playerInventory, host);
        this.host = host;
        for (int slot = 0; slot < TianshuSeedStorageBlockEntity.CELL_SLOTS; slot++) {
            cellSlots.add(addSlot(
                    new AppEngSlot(host.getCellInventory(), slot), SlotSemantics.STORAGE_CELL));
        }
        createPlayerInventorySlots(playerInventory);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (isClientSide() || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        var sourceSlot = getSlot(index);
        if (!sourceSlot.hasItem() || !sourceSlot.mayPickup(player)) return ItemStack.EMPTY;
        var original = sourceSlot.getItem().copy();
        ItemStack remainder;
        if (isPlayerSideSlot(sourceSlot)) {
            if (!StorageCells.isCellHandled(original)) return ItemStack.EMPTY;
            remainder = moveIntoSlots(original.copy(), cellSlots);
        } else {
            remainder = moveIntoSlots(original.copy(), getPlayerDestinationSlots());
        }
        int moved = original.getCount() - remainder.getCount();
        if (moved <= 0) return ItemStack.EMPTY;
        sourceSlot.remove(moved);
        sourceSlot.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return !host.isRemoved() && host.getLevel() != null
                && host.getLevel().getBlockEntity(host.getBlockPos()) == host
                && player.level() == host.getLevel()
                && player.distanceToSqr(host.getBlockPos().getCenter()) <= 64.0D;
    }

    private List<Slot> getPlayerDestinationSlots() {
        var result = new ArrayList<Slot>(getSlots(SlotSemantics.PLAYER_INVENTORY));
        result.addAll(getSlots(SlotSemantics.PLAYER_HOTBAR));
        return result;
    }

    private static ItemStack moveIntoSlots(ItemStack stack, List<Slot> destinations) {
        ItemStack remainder = stack;
        for (Slot slot : destinations) {
            if (!slot.hasItem()) continue;
            remainder = slot.safeInsert(remainder);
            if (remainder.isEmpty()) return ItemStack.EMPTY;
        }
        for (Slot slot : destinations) {
            if (slot.hasItem()) continue;
            remainder = slot.safeInsert(remainder);
            if (remainder.isEmpty()) return ItemStack.EMPTY;
        }
        return remainder;
    }
}
