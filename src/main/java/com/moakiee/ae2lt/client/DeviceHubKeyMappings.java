package com.moakiee.ae2lt.client;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.menu.hub.DeviceHubMenu;
import com.moakiee.ae2lt.network.DashPacket;
import com.moakiee.ae2lt.network.hub.OpenDeviceHubPacket;
import com.moakiee.ae2lt.celestweave.BaseCelestweaveArmorItem;

@EventBusSubscriber(modid = AE2LightningTech.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class DeviceHubKeyMappings {
    private static final String CATEGORY = "key.categories.ae2lt";

    public static final KeyMapping DASH = new KeyMapping(
            "key.ae2lt.dash",
            GLFW.GLFW_KEY_V,
            CATEGORY);

    public static final KeyMapping OPEN_CONFIG = new KeyMapping(
            "key.ae2lt.open_config",
            GLFW.GLFW_KEY_G,
            CATEGORY);

    private DeviceHubKeyMappings() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(DASH);
        event.register(OPEN_CONFIG);
    }

    @EventBusSubscriber(modid = AE2LightningTech.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
    public static final class RuntimeHandler {
        private RuntimeHandler() {
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            var minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.screen != null) {
                return;
            }

            while (DASH.consumeClick()) {
                PacketDistributor.sendToServer(new DashPacket());
            }

            while (OPEN_CONFIG.consumeClick()) {
                if (minecraft.player.getMainHandItem().getItem()
                        instanceof com.moakiee.ae2lt.item.railgun.ElectromagneticRailgunItem
                        || minecraft.player.getOffhandItem().getItem()
                        instanceof com.moakiee.ae2lt.item.railgun.ElectromagneticRailgunItem) {
                    PacketDistributor.sendToServer(new OpenDeviceHubPacket(DeviceHubMenu.TAB_RAILGUN));
                    continue;
                }

                int defaultTab = DeviceHubMenu.TAB_CHESTPLATE;
                for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.CHEST, EquipmentSlot.HEAD, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                    ItemStack armor = minecraft.player.getItemBySlot(slot);
                    if (armor.getItem() instanceof BaseCelestweaveArmorItem) {
                        defaultTab = switch (slot) {
                            case HEAD -> DeviceHubMenu.TAB_HELMET;
                            case CHEST -> DeviceHubMenu.TAB_CHESTPLATE;
                            case LEGS -> DeviceHubMenu.TAB_LEGGINGS;
                            case FEET -> DeviceHubMenu.TAB_BOOTS;
                            default -> DeviceHubMenu.TAB_CHESTPLATE;
                        };
                        break;
                    }
                }
                PacketDistributor.sendToServer(new OpenDeviceHubPacket(defaultTab));
            }
        }
    }
}
