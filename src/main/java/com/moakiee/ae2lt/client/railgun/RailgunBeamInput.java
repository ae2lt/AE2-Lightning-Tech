package com.moakiee.ae2lt.client.railgun;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.item.railgun.ElectromagneticRailgunItem;
import com.moakiee.ae2lt.network.railgun.RailgunBeamTogglePacket;

/**
 * Tracks the player's "left-click pressed" state while holding a railgun and
 * synchronizes it to the server via {@link RailgunBeamTogglePacket}.
 *
 * <p>While held, it also cancels the default attack interaction (so the gun
 * doesn't deal melee damage and doesn't swing the arm).
 */
@EventBusSubscriber(modid = AE2LightningTech.MODID, value = Dist.CLIENT)
public final class RailgunBeamInput {

    private static boolean firing = false;

    private RailgunBeamInput() {}

    public static void reset() {
        firing = false;
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Pre e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || e.getEntity() != mc.player) return;
        boolean holdingGun = mc.player.getMainHandItem().getItem() instanceof ElectromagneticRailgunItem;
        boolean attackPressed = mc.options.keyAttack.isDown() && mc.screen == null && holdingGun;
        if (attackPressed != firing) {
            firing = attackPressed;
            RailgunBeamRenderClient.setLocalRequestedFiring(firing);
            PacketDistributor.sendToServer(new RailgunBeamTogglePacket(firing, InteractionHand.MAIN_HAND));
        }
    }

    @SubscribeEvent
    public static void onAttackKeyPressed(InputEvent.InteractionKeyMappingTriggered e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!e.isAttack()) return;
        ItemStack main = mc.player.getMainHandItem();
        if (main.getItem() instanceof ElectromagneticRailgunItem) {
            e.setCanceled(true);
            e.setSwingHand(false);
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent e) {
        if (e.getEntity().getMainHandItem().getItem() instanceof ElectromagneticRailgunItem) {
            e.setCanceled(true);
        }
    }
}
