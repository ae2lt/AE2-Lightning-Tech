package com.moakiee.ae2lt.menu;

import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.slot.RestrictedInputSlot;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.integration.ae2wtlib.TianshuWTMenuHost;
import de.mari_023.ae2wtlib.AE2wtlibSlotSemantics;
import de.mari_023.ae2wtlib.terminal.WTMenuHost;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

/** Wireless variant of the Tianshu pattern-encoding terminal menu. */
public final class TianshuWirelessPatternEncodingTermMenu extends TianshuPatternEncodingTermMenu {
    private static final MenuTypeBuilder.MenuFactory<
            TianshuWirelessPatternEncodingTermMenu, TianshuWTMenuHost> FACTORY =
                    TianshuWirelessPatternEncodingTermMenu::new;

    public static final MenuType<TianshuWirelessPatternEncodingTermMenu> TYPE = Ae2ltMenuBuilder
            .buildUnregistered(
                    MenuTypeBuilder.create(
                            FACTORY, TianshuWTMenuHost.class),
                    new ResourceLocation(AE2LightningTech.MODID, "wireless_tianshu_pattern_encoding_terminal"));

    public TianshuWirelessPatternEncodingTermMenu(
            int id, Inventory inventory, TianshuWTMenuHost host) {
        super(TYPE, id, inventory, host);

        // MEStorageMenu already contributes the normal upgrade and view-cell slots. AE2WTLib's
        // entangled singularity is an additional segmented inventory owned by WTMenuHost.
        addSlot(new RestrictedInputSlot(
                        RestrictedInputSlot.PlacableItemType.QE_SINGULARITY,
                        host.getSubInventory(WTMenuHost.INV_SINGULARITY), 0),
                AE2wtlibSlotSemantics.SINGULARITY);
    }

    public boolean isWUT() {
        return tianshuHost.isUniversalWirelessTerminal();
    }
}
