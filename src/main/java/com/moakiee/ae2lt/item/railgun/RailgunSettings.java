package com.moakiee.ae2lt.item.railgun;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.registry.ModDataComponents;

public record RailgunSettings(
        boolean terrainDestruction,
        boolean pvp,
        boolean soundEnabled,
        boolean forceOverloadRemoval,
        boolean overloadImpactTargeting,
        boolean chainDamage) {

    public static final RailgunSettings DEFAULT = new RailgunSettings(false, false, true, false, true, true);

    public static final Codec<RailgunSettings> CODEC = RecordCodecBuilder.create(b -> b.group(
            Codec.BOOL.fieldOf("terrain").forGetter(RailgunSettings::terrainDestruction),
            Codec.BOOL.fieldOf("pvp").forGetter(RailgunSettings::pvp),
            Codec.BOOL.optionalFieldOf("sound", true).forGetter(RailgunSettings::soundEnabled),
            Codec.BOOL.optionalFieldOf("force_overload_removal", false)
                    .forGetter(RailgunSettings::forceOverloadRemoval),
            Codec.BOOL.optionalFieldOf("overload_impact_targeting", true)
                    .forGetter(RailgunSettings::overloadImpactTargeting),
            Codec.BOOL.optionalFieldOf("chain_damage", true).forGetter(RailgunSettings::chainDamage))
            .apply(b, RailgunSettings::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, RailgunSettings> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, RailgunSettings::terrainDestruction,
            ByteBufCodecs.BOOL, RailgunSettings::pvp,
            ByteBufCodecs.BOOL, RailgunSettings::soundEnabled,
            ByteBufCodecs.BOOL, RailgunSettings::forceOverloadRemoval,
            ByteBufCodecs.BOOL, RailgunSettings::overloadImpactTargeting,
            ByteBufCodecs.BOOL, RailgunSettings::chainDamage,
            RailgunSettings::new);

    public static boolean soundEnabled(ItemStack stack) {
        return stack == null
                || stack.isEmpty()
                || stack.getOrDefault(ModDataComponents.RAILGUN_SETTINGS.get(), DEFAULT).soundEnabled();
    }

    public RailgunSettings withTerrain(boolean v) {
        return new RailgunSettings(
                v, this.pvp, this.soundEnabled, this.forceOverloadRemoval, this.overloadImpactTargeting,
                this.chainDamage);
    }

    public RailgunSettings withPvp(boolean v) {
        return new RailgunSettings(
                this.terrainDestruction, v, this.soundEnabled, this.forceOverloadRemoval,
                this.overloadImpactTargeting, this.chainDamage);
    }

    public RailgunSettings withSound(boolean v) {
        return new RailgunSettings(
                this.terrainDestruction, this.pvp, v, this.forceOverloadRemoval, this.overloadImpactTargeting,
                this.chainDamage);
    }

    public RailgunSettings withForceOverloadRemoval(boolean v) {
        return new RailgunSettings(
                this.terrainDestruction, this.pvp, this.soundEnabled, v, this.overloadImpactTargeting,
                this.chainDamage);
    }

    public RailgunSettings withOverloadImpactTargeting(boolean v) {
        return new RailgunSettings(
                this.terrainDestruction, this.pvp, this.soundEnabled, this.forceOverloadRemoval, v,
                this.chainDamage);
    }

    public RailgunSettings withChainDamage(boolean v) {
        return new RailgunSettings(
                this.terrainDestruction, this.pvp, this.soundEnabled, this.forceOverloadRemoval,
                this.overloadImpactTargeting, v);
    }

    /** Player targeting requires both this railgun's opt-in and server permission. */
    public boolean allowsPlayerTargets(boolean serverAllowsPvp) {
        return this.pvp && serverAllowsPvp;
    }
}
