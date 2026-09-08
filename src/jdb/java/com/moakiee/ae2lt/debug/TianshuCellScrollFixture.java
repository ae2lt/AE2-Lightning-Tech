package com.moakiee.ae2lt.debug;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;
import com.moakiee.ae2lt.item.OverloadedFilterComponentItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Development-only real item with eight card slots, for the scroll boundary acceptance test. */
@EventBusSubscriber(modid = "ae2lt", bus = EventBusSubscriber.Bus.MOD)
public final class TianshuCellScrollFixture {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("ae2lt", "test_scroll_cell");

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.ITEM, helper -> helper.register(ID, new OverloadedFilterComponentItem(new Item.Properties()) {
            @Override public IUpgradeInventory getUpgrades(ItemStack stack) { return UpgradeInventories.forItem(stack, 8); }
        }));
    }

    @SubscribeEvent
    public static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            var item = BuiltInRegistries.ITEM.get(ID);
            Upgrades.add(AEItems.FUZZY_CARD, item, 1);
            Upgrades.add(AEItems.SPEED_CARD, item, 8);
        });
    }

    public static ItemStack stack() { return new ItemStack(BuiltInRegistries.ITEM.get(ID)); }
}
