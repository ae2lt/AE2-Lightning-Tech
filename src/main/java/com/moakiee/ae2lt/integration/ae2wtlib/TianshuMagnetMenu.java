package com.moakiee.ae2lt.integration.ae2wtlib;

import appeng.menu.SlotSemantic;
import de.mari_023.ae2wtlib.wct.magnet_card.MagnetMenu;
import java.util.List;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Native slots and actions, presented inside the existing terminal container. */
public final class TianshuMagnetMenu extends MagnetMenu {
    static final List<String> ACTIONS = List.of("togglepickupmode", "toggleinsertmode", "copy_up", "copy_down", "switch");
    private final TianshuEnhancedWirelessCraftingMenu terminalMenu;
    private boolean clientView;

    TianshuMagnetMenu(TianshuEnhancedWirelessCraftingMenu terminalMenu) {
        super(terminalMenu.containerId, terminalMenu.getPlayerInventory(),
                new TianshuMagnetMenuHost(terminalMenu.getPlayer(), terminalMenu.getWirelessHost()));
        this.terminalMenu = terminalMenu;
    }

    public TianshuMagnetMenu asClientView() {
        if (!isClientSide()) throw new IllegalStateException("The magnet view is client-only");
        // Preserve the terminal's slot IDs: its menu remains the player's synchronized container.
        // Refresh on every visit because resizing the ME screen can replace its client-only slots.
        // No slots are added to the terminal here, and no second container is opened.
        slots.clear();
        slots.addAll(terminalMenu.slots);
        clientView = true;
        return this;
    }

    @Override public List<Slot> getSlots(SlotSemantic semantic) {
        return clientView ? terminalMenu.getSlots(semantic) : super.getSlots(semantic);
    }
    @Override public SlotSemantic getSlotSemantic(Slot slot) {
        return clientView ? terminalMenu.getSlotSemantic(slot) : super.getSlotSemantic(slot);
    }
    @Override public ItemStack getCarried() {
        return clientView ? terminalMenu.getCarried() : super.getCarried();
    }
    @Override public void setCarried(ItemStack stack) {
        if (clientView) terminalMenu.setCarried(stack); else super.setCarried(stack);
    }
    @Override public int getStateId() {
        return clientView ? terminalMenu.getStateId() : super.getStateId();
    }
}
