package com.moakiee.ae2lt.celestweave.service;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;

import com.moakiee.ae2lt.device.network.ArmorNetworkBinding;
import com.moakiee.ae2lt.me.key.LightningKey;

public final class ArmorLightningService {
    private ArmorLightningService() {
    }

    public static boolean hasCost(ServerPlayer player, ItemStack armor, LightningKey key, long amount) {
        if (amount <= 0L) {
            return true;
        }
        return extract(player, armor, key, amount, Actionable.SIMULATE) >= amount;
    }

    public static boolean hasCost(ServerPlayer player, ItemStack armor, LightningCost cost) {
        if (cost.isEmpty()) {
            return true;
        }
        return hasCost(player, armor, LightningKey.HIGH_VOLTAGE, cost.highVoltage())
                && hasCost(player, armor, LightningKey.EXTREME_HIGH_VOLTAGE, cost.extremeHighVoltage());
    }

    public static boolean consume(ServerPlayer player, ItemStack armor, LightningKey key, long amount) {
        if (amount <= 0L) {
            return true;
        }
        long got = extract(player, armor, key, amount, Actionable.MODULATE);
        if (got >= amount) {
            return true;
        }
        refund(player, armor, key, got);
        return false;
    }

    public static boolean consume(ServerPlayer player, ItemStack armor, LightningCost cost) {
        if (cost.isEmpty()) {
            return true;
        }
        if (!hasCost(player, armor, cost)) {
            return false;
        }
        long gotHv = extract(player, armor, LightningKey.HIGH_VOLTAGE, cost.highVoltage(), Actionable.MODULATE);
        if (gotHv < cost.highVoltage()) {
            refund(player, armor, LightningKey.HIGH_VOLTAGE, gotHv);
            return false;
        }
        long gotEhv = extract(
                player,
                armor,
                LightningKey.EXTREME_HIGH_VOLTAGE,
                cost.extremeHighVoltage(),
                Actionable.MODULATE);
        if (gotEhv < cost.extremeHighVoltage()) {
            refund(player, armor, LightningKey.HIGH_VOLTAGE, gotHv);
            refund(player, armor, LightningKey.EXTREME_HIGH_VOLTAGE, gotEhv);
            return false;
        }
        return true;
    }

    private static long extract(ServerPlayer player, ItemStack armor, LightningKey key, long amount, Actionable action) {
        if (player == null || armor == null || armor.isEmpty() || key == null || amount <= 0L) {
            return 0L;
        }
        var bound = ArmorNetworkBinding.INSTANCE.resolve(armor, player);
        if (!bound.success() || bound.grid() == null) {
            return 0L;
        }
        return bound.grid().getStorageService().getInventory().extract(
                key,
                amount,
                action,
                IActionSource.ofPlayer(player));
    }

    private static void refund(ServerPlayer player, ItemStack armor, LightningKey key, long amount) {
        if (player == null || armor == null || armor.isEmpty() || key == null || amount <= 0L) {
            return;
        }
        var bound = ArmorNetworkBinding.INSTANCE.resolve(armor, player);
        if (!bound.success() || bound.grid() == null) {
            return;
        }
        bound.grid().getStorageService().getInventory().insert(
                key,
                amount,
                Actionable.MODULATE,
                IActionSource.ofPlayer(player));
    }

    public record LightningCost(long highVoltage, long extremeHighVoltage) {
        public static final LightningCost NONE = new LightningCost(0L, 0L);

        public LightningCost {
            highVoltage = Math.max(0L, highVoltage);
            extremeHighVoltage = Math.max(0L, extremeHighVoltage);
        }

        public static LightningCost hv(long amount) {
            return new LightningCost(amount, 0L);
        }

        public static LightningCost ehv(long amount) {
            return new LightningCost(0L, amount);
        }

        public LightningCost plus(LightningCost other) {
            if (other == null || other.isEmpty()) {
                return this;
            }
            return new LightningCost(
                    saturatingAdd(highVoltage, other.highVoltage),
                    saturatingAdd(extremeHighVoltage, other.extremeHighVoltage));
        }

        public LightningCost times(long multiplier) {
            if (multiplier <= 0L || isEmpty()) {
                return NONE;
            }
            return new LightningCost(
                    saturatingMultiply(highVoltage, multiplier),
                    saturatingMultiply(extremeHighVoltage, multiplier));
        }

        public boolean isEmpty() {
            return highVoltage <= 0L && extremeHighVoltage <= 0L;
        }

        private static long saturatingAdd(long left, long right) {
            if (left > Long.MAX_VALUE - right) {
                return Long.MAX_VALUE;
            }
            return left + right;
        }

        private static long saturatingMultiply(long value, long multiplier) {
            if (value <= 0L || multiplier <= 0L) {
                return 0L;
            }
            if (value > Long.MAX_VALUE / multiplier) {
                return Long.MAX_VALUE;
            }
            return value * multiplier;
        }
    }
}
