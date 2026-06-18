package com.moakiee.ae2lt.celestweave.service;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.celestweave.service.ArmorLightningService.LightningCost;
import com.moakiee.ae2lt.me.key.LightningKey;

public final class ArmorResourceFeedback {
    private static final int COOLDOWN_TICKS = 40;
    private static final String TAG_PREFIX = "ae2lt.armor_resource_feedback.";

    private ArmorResourceFeedback() {
    }

    public static void noFe(ServerPlayer player) {
        notify(player, "ae2lt.celestweave.fail.no_fe", "fe");
    }

    public static void noHighVoltage(ServerPlayer player) {
        notify(player, "ae2lt.celestweave.fail.no_hv", "hv");
    }

    public static void noExtremeHighVoltage(ServerPlayer player) {
        notify(player, "ae2lt.celestweave.fail.no_ehv", "ehv");
    }

    public static void noLightning(ServerPlayer player, ItemStack armor, LightningCost cost) {
        if (cost == null || cost.isEmpty()) {
            return;
        }
        if (cost.highVoltage() > 0L
                && !ArmorLightningService.hasCost(player, armor, LightningKey.HIGH_VOLTAGE, cost.highVoltage())) {
            noHighVoltage(player);
            return;
        }
        if (cost.extremeHighVoltage() > 0L
                && !ArmorLightningService.hasCost(
                        player,
                        armor,
                        LightningKey.EXTREME_HIGH_VOLTAGE,
                        cost.extremeHighVoltage())) {
            noExtremeHighVoltage(player);
            return;
        }
        if (cost.highVoltage() > 0L) {
            noHighVoltage(player);
        } else if (cost.extremeHighVoltage() > 0L) {
            noExtremeHighVoltage(player);
        }
    }

    private static void notify(ServerPlayer player, String key, String resource) {
        if (player == null) {
            return;
        }
        long now = player.level().getGameTime();
        String tag = TAG_PREFIX + resource;
        if (player.getPersistentData().getLong(tag) > now) {
            return;
        }
        player.getPersistentData().putLong(tag, saturatingAdd(now, COOLDOWN_TICKS));
        player.displayClientMessage(Component.translatable(key), true);
    }

    private static long saturatingAdd(long left, long right) {
        if (right <= 0L) {
            return left;
        }
        if (left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }
}
