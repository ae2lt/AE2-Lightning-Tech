package com.moakiee.ae2lt.client;
import com.moakiee.ae2lt.network.NetworkInit;

import org.lwjgl.glfw.GLFW;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.network.ToggleFrequencyCardAutoConnectPacket;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;

@Mod.EventBusSubscriber(modid = AE2LightningTech.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FrequencyCardKeyMappings {
    private static final String CATEGORY = "key.categories.ae2lt";

    private static final KeyMapping TOGGLE_AUTO_CONNECT = new KeyMapping(
            "key.ae2lt.toggle_frequency_card_auto_connect",
            GLFW.GLFW_KEY_UNKNOWN,
            CATEGORY);

    private FrequencyCardKeyMappings() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_AUTO_CONNECT);
    }

    @Mod.EventBusSubscriber(modid = AE2LightningTech.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class RuntimeHandler {
        private RuntimeHandler() {
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            while (TOGGLE_AUTO_CONNECT.consumeClick()) {
                NetworkInit.sendToServer(ToggleFrequencyCardAutoConnectPacket.forPreferredCard());
            }
        }
    }
}
