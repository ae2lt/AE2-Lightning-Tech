package com.moakiee.ae2lt.item.staff;

import com.moakiee.ae2lt.AE2LightningTech;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class StaffEnergyEvents {
    private StaffEnergyEvents() {}

    private static boolean exhausted(Player player) {
        var stack = player.getMainHandItem();
        return stack.getItem() instanceof MimicryStaffItem && !StaffEnergy.canUse(stack);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void attack(AttackEntityEvent event) {
        if (exhausted(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void breakBlock(BlockEvent.BreakEvent event) {
        if (exhausted(event.getPlayer())) event.setCanceled(true);
    }
}
