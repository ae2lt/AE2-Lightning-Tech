package com.moakiee.ae2lt.celestweave.module;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;

import com.moakiee.ae2lt.celestweave.CelestweaveArmorState;

public final class SaturationSubmodule extends AbstractCelestweaveArmorSubmodule {

    public static final SaturationSubmodule INSTANCE = new SaturationSubmodule();

    private static final String TAG_READY_AT_TICK = "SaturationCheckReadyAtTick";

    private SaturationSubmodule() {
    }

    @Override
    public String id() {
        return "saturation";
    }

    @Override
    public String nameKey() {
        return "ae2lt.celestweave.feature.saturation.name";
    }

    @Override
    public String descriptionKey() {
        return "ae2lt.celestweave.feature.saturation.desc";
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
        if (dist == Dist.DEDICATED_SERVER) {
            setCooldown(armor, player, 0);
        }
    }

    @Override
    public int tickActive(@Nullable Player player, Dist dist, ItemStack armor) {
        return 0;
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

    public static void setCooldown(ItemStack armor, @Nullable Player player, int ticks) {
        var data = CelestweaveArmorState.getSubmoduleData(armor, INSTANCE);
        if (ticks <= 0 || player == null) {
            data.remove(TAG_READY_AT_TICK);
        } else {
            data.putLong(TAG_READY_AT_TICK, player.level().getGameTime() + ticks);
        }
        CelestweaveArmorState.setSubmoduleData(armor, INSTANCE, data);
    }
}
