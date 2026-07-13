package com.moakiee.ae2lt.integration.ae2wtlib;

import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.slot.RestrictedInputSlot;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import de.mari_023.ae2wtlib.api.gui.AE2wtlibSlotSemantics;
import de.mari_023.ae2wtlib.api.terminal.ItemWUT;
import de.mari_023.ae2wtlib.api.terminal.WTMenuHost;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

public final class TianshuWirelessPatternEncodingTermMenu extends TianshuPatternEncodingTermMenu {
    private static final MenuTypeBuilder.MenuFactory<
            TianshuWirelessPatternEncodingTermMenu, TianshuWirelessMenuHost> FACTORY =
            TianshuWirelessPatternEncodingTermMenu::new;
    public static final MenuType<TianshuWirelessPatternEncodingTermMenu> TYPE = MenuTypeBuilder
            .create(FACTORY, TianshuWirelessMenuHost.class)
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(
                    AE2LightningTech.MODID, "tianshu_wireless_pattern_encoding_terminal"));

    private final TianshuWirelessMenuHost wirelessHost;

    public TianshuWirelessPatternEncodingTermMenu(
            int id, Inventory inventory, TianshuWirelessMenuHost host) {
        super(TYPE, id, inventory, host);
        this.wirelessHost = host;
        addSlot(new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.QE_SINGULARITY,
                host.getSubInventory(WTMenuHost.INV_SINGULARITY), 0), AE2wtlibSlotSemantics.SINGULARITY);
    }

    @Override public appeng.api.networking.IGridNode getGridNode() { return wirelessHost.getActionableNode(); }
    public boolean isWUT() { return wirelessHost.getItemStack().getItem() instanceof ItemWUT; }
}
