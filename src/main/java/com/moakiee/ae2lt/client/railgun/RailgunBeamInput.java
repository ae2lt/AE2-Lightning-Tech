package com.moakiee.ae2lt.client.railgun;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.TickEvent;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.item.railgun.ElectromagneticRailgunItem;
import com.moakiee.ae2lt.network.NetworkInit;
import com.moakiee.ae2lt.network.railgun.RailgunBeamTogglePacket;

/**
 * Tracks the player's "left-click pressed" state while holding a railgun and
 * synchronizes it to the server via {@link RailgunBeamTogglePacket}.
 *
 * <p>While held, it also cancels the default attack interaction (so the gun
 * doesn't deal melee damage and doesn't swing the arm).
 */
@Mod.EventBusSubscriber(modid = AE2LightningTech.MODID, value = Dist.CLIENT)
public final class RailgunBeamInput {

    private static boolean firing = false;

    private RailgunBeamInput() {}

    public static void reset() {
        firing = false;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || e.player != mc.player) return;
        boolean holdingGun = mc.player.getMainHandItem().getItem() instanceof ElectromagneticRailgunItem;
        boolean attackPressed = mc.options.keyAttack.isDown() && mc.screen == null && holdingGun;
        if (attackPressed != firing) {
            firing = attackPressed;
            RailgunBeamRenderClient.setLocalRequestedFiring(firing);
            NetworkInit.sendToServer(new RailgunBeamTogglePacket(firing, InteractionHand.MAIN_HAND));
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
