package com.moakiee.ae2lt.celestweave.module;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;

import com.moakiee.ae2lt.celestweave.ArmorOverloadRules;
import com.moakiee.ae2lt.celestweave.CelestweaveArmorState;
import com.moakiee.ae2lt.celestweave.service.ArmorEnergyService;
import com.moakiee.ae2lt.celestweave.service.ArmorResourceFeedback;

public final class DashSubmodule extends AbstractCelestweaveArmorSubmodule {

    public static final DashSubmodule INSTANCE = new DashSubmodule();

    private static final double IMPULSE = 1.8D;
    private static final int COOLDOWN_TICKS = 40;
    private static final String TAG_READY_AT_TICK = "DashReadyAtTick";

    private DashSubmodule() {}

    @Override
    public String id() {
        return "dash";
    }

    @Override
    public String nameKey() {
        return "ae2lt.celestweave.feature.dash.name";
    }

    @Override
    public String descriptionKey() {
        return "ae2lt.celestweave.feature.dash.desc";
    }

    @Override
    public boolean defaultEnabled() {
        return true;
    }

    @Override
    public int getMaxInstallAmount() {
        return 1;
    }

    @Override
    public void onDeactivated(@Nullable Player player, Dist dist, ItemStack armor) {
        if (player != null && dist == Dist.DEDICATED_SERVER) {
            setCooldown(armor, player, 0);
        }
    }

    @Override
    public int tickActive(@Nullable Player player, Dist dist, ItemStack armor) {
        return 0;
    }

    public static void applyDash(ServerPlayer player, ItemStack armor) {
        var sub = INSTANCE;
        if (!sub.isActive(armor)) return;
        if (getCooldown(armor, player) > 0) {
            player.displayClientMessage(Component.translatable("ae2lt.celestweave.feature.dash.cooldown"), true);
            return;
        }
        long feCost = ArmorOverloadRules.DASH_ACTIVE_COST_FE;
        var payment = ArmorEnergyService.consumeActiveCostPayment(player, armor, feCost);
        if (!payment.paid()) {
            ArmorResourceFeedback.noFe(player);
            return;
        }
        var look = player.getLookAngle();
        player.setDeltaMovement(
                player.getDeltaMovement().x + look.x * IMPULSE,
                Math.max(player.getDeltaMovement().y, 0.0D) + 0.3D,
                player.getDeltaMovement().z + look.z * IMPULSE);
        player.hurtMarked = true;
        player.resetFallDistance();
        setCooldown(armor, player, COOLDOWN_TICKS);
    }

    /**
     * @deprecated Cooldowns are stored as expiry game ticks; use {@link #getCooldown(ItemStack, Player)}.
     */
    @Deprecated
    public static int getCooldown(ItemStack armor) {
        return 0;
    }

    public static int getCooldown(ItemStack armor, @Nullable Player player) {
        if (player == null) {
            return 0;
        }
        var data = CelestweaveArmorState.getSubmoduleData(armor, INSTANCE);
        if (!data.contains(TAG_READY_AT_TICK, CompoundTag.TAG_LONG)) {
            return 0;
        }
        long remaining = data.getLong(TAG_READY_AT_TICK) - player.level().getGameTime();
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, remaining));
    }

    private static void setCooldown(ItemStack armor, @Nullable Player player, int ticks) {
        var data = CelestweaveArmorState.getSubmoduleData(armor, INSTANCE);
        if (ticks <= 0 || player == null) {
            data.remove(TAG_READY_AT_TICK);
        } else {
            data.putLong(TAG_READY_AT_TICK, player.level().getGameTime() + ticks);
        }
        CelestweaveArmorState.setSubmoduleData(armor, INSTANCE, data);
    }
}
