package com.moakiee.ae2lt.debug;

import com.moakiee.ae2lt.celestweave.CelestweaveArmorState;
import com.moakiee.ae2lt.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Real equipment mutation confined to the development preview world. */
public final class CelestweaveLightningPreview {
    public static String core(boolean installed) {
        var mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        if (server == null || mc.player == null
                || !server.getWorldData().getLevelName().equals("Celestweave-Field-Preview")) {
            return "Not in preview world";
        }
        var id = mc.player.getUUID();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(id);
            if (player == null) return;
            var chest = player.getItemBySlot(EquipmentSlot.CHEST);
            CelestweaveArmorState.setSlot(chest, player.registryAccess(), CelestweaveArmorState.SLOT_CORE,
                    installed ? new ItemStack(ModItems.ULTIMATE_OVERLOAD_CORE.get()) : ItemStack.EMPTY);
            player.containerMenu.broadcastChanges();
        });
        return "Queued actual chest structural core: " + installed;
    }

    public static String status() {
        var mc = Minecraft.getInstance();
        return CelestweaveFieldPreview.status() + "; chestCore=" + (mc.player != null
                && CelestweaveArmorState.hasCore(mc.player.getItemBySlot(EquipmentSlot.CHEST), mc.player.registryAccess()));
    }
}
