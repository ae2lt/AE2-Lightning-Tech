package com.moakiee.ae2lt.menu;

import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.slot.RestrictedInputSlot;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWirelessPatternEncodingTermMenuHost;
import de.mari_023.ae2wtlib.api.gui.AE2wtlibSlotSemantics;
import de.mari_023.ae2wtlib.api.terminal.ItemWUT;
import de.mari_023.ae2wtlib.api.terminal.WTMenuHost;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

/** Tianshu menu variant with AE2WTLib upgrade and singularity slots. */
public final class TianshuWirelessPatternEncodingTermMenu extends TianshuPatternEncodingTermMenu {
    private static final MenuTypeBuilder.MenuFactory<
            TianshuWirelessPatternEncodingTermMenu, TianshuWirelessPatternEncodingTermMenuHost> FACTORY =
                    TianshuWirelessPatternEncodingTermMenu::new;

    public static final MenuType<TianshuWirelessPatternEncodingTermMenu> TYPE = MenuTypeBuilder
            .create(FACTORY, TianshuWirelessPatternEncodingTermMenuHost.class)
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(
                    AE2LightningTech.MODID, "wireless_tianshu_pattern_encoding_terminal"));

    private final TianshuWirelessPatternEncodingTermMenuHost wirelessHost;

    public TianshuWirelessPatternEncodingTermMenu(
            int id, Inventory inventory, TianshuWirelessPatternEncodingTermMenuHost host) {
        super(TYPE, id, inventory, host);
        this.wirelessHost = host;

        // MEStorageMenu creates the regular upgrade slots from host.getUpgrades(). AE2WTLib
        // contributes one additional slot for its entangled singularity.
        addSlot(new RestrictedInputSlot(
                        RestrictedInputSlot.PlacableItemType.QE_SINGULARITY,
                        host.getSubInventory(WTMenuHost.INV_SINGULARITY), 0),
                AE2wtlibSlotSemantics.SINGULARITY);
    }

    public boolean isWUT() {
        return wirelessHost.getItemStack().getItem() instanceof ItemWUT;
    }
}
