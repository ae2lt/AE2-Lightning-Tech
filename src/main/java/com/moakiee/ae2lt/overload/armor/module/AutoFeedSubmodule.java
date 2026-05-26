package com.moakiee.ae2lt.overload.armor.module;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;

import com.moakiee.ae2lt.overload.armor.OverloadArmorState;

public final class AutoFeedSubmodule extends AbstractOverloadArmorSubmodule {

    public static final AutoFeedSubmodule INSTANCE = new AutoFeedSubmodule();

    private static final String TAG_COOLDOWN = "AutoFeedCooldown";

    private AutoFeedSubmodule() {
    }

    @Override
    public String id() {
        return "auto_feed";
    }

    @Override
    public String nameKey() {
        return "ae2lt.overload_armor.feature.auto_feed.name";
    }

    @Override
    public String descriptionKey() {
        return "ae2lt.overload_armor.feature.auto_feed.desc";
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
            setCooldown(armor, 0);
        }
    }

    @Override
    public int tickActive(@Nullable Player player, Dist dist, ItemStack armor) {
        if (dist == Dist.DEDICATED_SERVER) {
            int cooldown = getCooldown(armor);
            if (cooldown > 0) {
                setCooldown(armor, cooldown - 1);
            }
        }
        return 0;
    }

    public static int getCooldown(ItemStack armor) {
        var data = OverloadArmorState.getSubmoduleData(armor, INSTANCE);
        return data.contains(TAG_COOLDOWN, CompoundTag.TAG_INT) ? data.getInt(TAG_COOLDOWN) : 0;
    }

    public static void setCooldown(ItemStack armor, int ticks) {
        var data = OverloadArmorState.getSubmoduleData(armor, INSTANCE);
        if (ticks <= 0) {
            data.remove(TAG_COOLDOWN);
        } else {
            data.putInt(TAG_COOLDOWN, ticks);
        }
        OverloadArmorState.setSubmoduleData(armor, INSTANCE, data);
    }
}
