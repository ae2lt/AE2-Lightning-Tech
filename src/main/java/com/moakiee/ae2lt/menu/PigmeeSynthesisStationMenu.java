package com.moakiee.ae2lt.menu;

import appeng.menu.me.items.CraftingTermMenu;
import appeng.helpers.ICraftingGridMenu;
import java.util.List;
import appeng.menu.implementations.MenuTypeBuilder;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.blockentity.PigmeeSynthesisStationBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

/** AE2 crafting-terminal menu backed by a Pigmee station's adjacent capability. */
public final class PigmeeSynthesisStationMenu extends CraftingTermMenu {
    public static final MenuType<PigmeeSynthesisStationMenu> TYPE = MenuTypeBuilder
            .create(PigmeeSynthesisStationMenu::new, PigmeeSynthesisStationBlockEntity.class)
            .withMenuTitle(host -> Component.translatable(
                    "block.ae2lt.pigmee_synthesis_station"))
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(
                    AE2LightningTech.MODID, "pigmee_synthesis_station"));

    @Override
    protected boolean showsCraftables() {
        return false;
    }

    @Override
    public void startAutoCrafting(List<ICraftingGridMenu.AutoCraftEntry> toCraft) {
        // This standalone station has no ME crafting service.
    }

    public PigmeeSynthesisStationMenu(
            int id,
            Inventory playerInventory,
            PigmeeSynthesisStationBlockEntity host) {
        super(TYPE, id, playerInventory, host, true);
    }
}
