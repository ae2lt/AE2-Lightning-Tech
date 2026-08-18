package com.moakiee.ae2lt.client;
import com.moakiee.ae2lt.network.NetworkInit;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.item.OverloadedFrequencyCardItem;
import com.moakiee.ae2lt.network.ToggleFrequencyCardAutoConnectPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.InputEvent;

@Mod.EventBusSubscriber(modid = AE2LightningTech.MODID, value = Dist.CLIENT)
public final class FrequencyCardScrollHandler {
    private FrequencyCardScrollHandler() {
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (event.getScrollDelta() == 0.0D || !Screen.hasShiftDown()) {
            return;
        }

        var player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        InteractionHand hand = null;
        if (player.getMainHandItem().getItem() instanceof OverloadedFrequencyCardItem) {
            hand = InteractionHand.MAIN_HAND;
        } else if (player.getOffhandItem().getItem() instanceof OverloadedFrequencyCardItem) {
            hand = InteractionHand.OFF_HAND;
        }

        if (hand == null) {
            return;
        }

        NetworkInit.sendToServer(ToggleFrequencyCardAutoConnectPacket.forHand(hand));
        event.setCanceled(true);
    }
}
