package com.moakiee.ae2lt.menu;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.me.cell.VoidCellData;
import com.moakiee.ae2lt.me.cell.VoidCellMode;

/** Server-authoritative item menu for selecting the void cell output mode. */
public final class VoidCellMenu extends AEBaseMenu {
    private static final String ACTION_SET_MODE = "setMode";

    public static final MenuType<VoidCellMenu> TYPE = Ae2ltMenuBuilder.buildUnregistered(
            MenuTypeBuilder.create(VoidCellMenu::new, ItemMenuHost.class),
            new ResourceLocation(AE2LightningTech.MODID, "void_cell"));

    private final ItemStack stack;

    @GuiSync(1)
    private int mode;

    public VoidCellMenu(int id, Inventory playerInventory, ItemMenuHost host) {
        super(TYPE, id, playerInventory, host);
        this.stack = host.getItemStack();
        this.mode = VoidCellData.readMode(stack).ordinal();
        registerClientAction(ACTION_SET_MODE, Integer.class, this::setModeFromClient);
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            mode = VoidCellData.readMode(stack).ordinal();
        }
        super.broadcastChanges();
    }

    public VoidCellMode getMode() {
        return VoidCellMode.fromOrdinal(mode);
    }

    public void selectMode(VoidCellMode selectedMode) {
        if (isClientSide()) {
            mode = selectedMode.ordinal();
            sendClientAction(ACTION_SET_MODE, mode);
        } else {
            setMode(selectedMode);
        }
    }

    private void setModeFromClient(int ordinal) {
        if (isServerSide() && ordinal >= 0 && ordinal < VoidCellMode.values().length) {
            setMode(VoidCellMode.values()[ordinal]);
        }
    }

    private void setMode(VoidCellMode selectedMode) {
        VoidCellData.writeMode(stack, selectedMode);
        mode = selectedMode.ordinal();
        broadcastChanges();
    }
}
