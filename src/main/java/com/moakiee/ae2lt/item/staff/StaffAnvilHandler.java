package com.moakiee.ae2lt.item.staff;

import com.moakiee.ae2lt.AE2LightningTech;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;

@EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class StaffAnvilHandler {
    private StaffAnvilHandler() {}

    @SubscribeEvent
    public static void validateEnchanting(AnvilUpdateEvent event) {
        var left = event.getLeft();
        var right = event.getRight();
        if (left.getItem() instanceof MimicryStaffItem) {
            // Vanilla creative anvils bypass supportsEnchantment. Renaming remains available, but even creative mode must not attach books to the staff itself.
            if (right.has(DataComponents.STORED_ENCHANTMENTS)
                    || !right.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty()
                    || right.getItem() instanceof MimicryStaffItem && StaffState.modules(right).stream().anyMatch(s -> !s.isEmpty())) {
                event.setCanceled(true);
            }
        } else if (left.getItem() instanceof StaffModuleItem module) {
            var books = right.getOrDefault(DataComponents.STORED_ENCHANTMENTS,
                    right.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY));
            if (books.keySet().stream().anyMatch(enchantment -> !StaffEnchantments.accepts(module.module(), enchantment))) {
                event.setCanceled(true);
            }
        }
    }
}
