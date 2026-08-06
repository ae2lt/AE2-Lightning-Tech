package com.moakiee.ae2lt.event;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.config.AE2LTConfigMigration;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class ConfigMigrationNoticeHandler {
    private static final String NOTIFIED_TAG = "ae2lt.config_migrated_v2_notified";

    private ConfigMigrationNoticeHandler() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!AE2LTConfigMigration.migrationOccurred()) {
            return;
        }
        if (event.getEntity() instanceof FakePlayer
                || !(event.getEntity() instanceof ServerPlayer serverPlayer)) {
            return;
        }
        var data = serverPlayer.getPersistentData();
        if (data.getBoolean(NOTIFIED_TAG)) {
            return;
        }
        serverPlayer.sendSystemMessage(Component.translatable("message.ae2lt.config_migrated_v2"));
        serverPlayer.sendSystemMessage(Component.translatable("message.ae2lt.config_migrated_v2.matrix"));
        data.putBoolean(NOTIFIED_TAG, true);
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.getOriginal().getPersistentData().getBoolean(NOTIFIED_TAG)) {
            event.getEntity().getPersistentData().putBoolean(NOTIFIED_TAG, true);
        }
    }
}
