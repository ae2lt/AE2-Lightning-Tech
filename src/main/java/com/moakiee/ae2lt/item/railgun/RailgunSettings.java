package com.moakiee.ae2lt.item.railgun;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.registry.ModDataComponents;

public record RailgunSettings(
        boolean terrainDestruction,
        boolean pvp,
        boolean soundEnabled,
        boolean forceOverloadRemoval,
        boolean chargedSplash,
        boolean chainDamage) {

    public static final RailgunSettings DEFAULT = new RailgunSettings(false, false, true, false, true, true);

    public static final Codec<RailgunSettings> CODEC = RecordCodecBuilder.create(b -> b.group(
            Codec.BOOL.fieldOf("terrain").forGetter(RailgunSettings::terrainDestruction),
            Codec.BOOL.fieldOf("pvp").forGetter(RailgunSettings::pvp),
            Codec.BOOL.optionalFieldOf("sound", true).forGetter(RailgunSettings::soundEnabled),
            Codec.BOOL.optionalFieldOf("force_overload_removal", false)
                    .forGetter(RailgunSettings::forceOverloadRemoval),
            Codec.BOOL.optionalFieldOf("charged_splash", true)
                    .forGetter(RailgunSettings::chargedSplash),
            Codec.BOOL.optionalFieldOf("chain_damage", true).forGetter(RailgunSettings::chainDamage))
            .apply(b, RailgunSettings::new));

    // 1.20.1 移植：STREAM_CODEC 已删除（1.21 API），客户端同步走 ItemStack NBT 全量同步。

    public static boolean soundEnabled(ItemStack stack) {
        return stack == null
                || stack.isEmpty()
                || ModDataComponents.RAILGUN_SETTINGS.getOrDefault(stack, DEFAULT).soundEnabled();
    }

    public RailgunSettings withTerrain(boolean v) {
        return new RailgunSettings(
                v, this.pvp, this.soundEnabled, this.forceOverloadRemoval, this.chargedSplash,
                this.chainDamage);
    }

    public RailgunSettings withPvp(boolean v) {
        return new RailgunSettings(
                this.terrainDestruction, v, this.soundEnabled, this.forceOverloadRemoval,
                this.chargedSplash, this.chainDamage);
    }

    public RailgunSettings withSound(boolean v) {
        return new RailgunSettings(
                this.terrainDestruction, this.pvp, v, this.forceOverloadRemoval, this.chargedSplash,
                this.chainDamage);
    }

    public RailgunSettings withForceOverloadRemoval(boolean v) {
        return new RailgunSettings(
                this.terrainDestruction, this.pvp, this.soundEnabled, v, this.chargedSplash,
                this.chainDamage);
    }

    public RailgunSettings withChargedSplash(boolean v) {
        return new RailgunSettings(
                this.terrainDestruction, this.pvp, this.soundEnabled, this.forceOverloadRemoval, v,
                this.chainDamage);
    }

    public RailgunSettings withChainDamage(boolean v) {
        return new RailgunSettings(
                this.terrainDestruction, this.pvp, this.soundEnabled, this.forceOverloadRemoval,
                this.chargedSplash, v);
    }

    /** Player targeting requires both this railgun's opt-in and server permission. */
    public boolean allowsPlayerTargets(boolean serverAllowsPvp) {
        return this.pvp && serverAllowsPvp;
    }
}
